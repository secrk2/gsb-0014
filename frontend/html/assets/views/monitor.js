/* ============================================================
 * 定位监控（干警/监管员）
 * - 腕表每 5 秒回传一帧：总览 5s 轮询，轨迹 since 增量追加
 * - SVG 等距投影自绘：多边形活动范围/禁区、轨迹线、越界与禁区段、当前点脉冲
 * - 周(7d)/月(30d) 两视图；越界/禁区落点二次确认填原因留痕（服务端重算）
 * - 三种空态分开：加载失败（可重试）/ 该对象从无轨迹 / 轨迹已清除（带清除留痕）
 * - 在矫完成度双口径并列，口径定义上墙；两口径相反时显著提示
 * - 所有时间按“对象所属司法所时区”显示，不按干警浏览器时区
 * ============================================================ */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const POLL_MS = 5000;
  let pollTimer = null;
  let overviewTimer = null;

  const LINK_META = {
    ONLINE: { label: '实时在线', cls: 'green', icon: '🟢' },
    STALE: { label: '信号延迟', cls: 'warn', icon: '🟡' },
    OFFLINE: { label: '离线', cls: 'red', icon: '🔴' },
    NEVER: { label: '从无回传', cls: 'gray', icon: '⚪' },
  };
  const CONCLUSIONS = [
    { v: 'REALLY_BREACH', label: '确认越界/闯禁区，派警处置' },
    { v: 'FALSE_ALARM', label: '误报排除（GPS 漂移/围栏边界误差）' },
    { v: 'ESCORT_APPROVED', label: '押解/请假批准范围内，核销预警' },
  ];

  Views.monitor = async function (root, routeId) {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    if (overviewTimer) { clearInterval(overviewTimer); overviewTimer = null; }

    const session = Api.getSession();
    const state = {
      offices: [],
      items: [],
      selectedId: routeId ? Number(routeId) : null,
      range: 'WEEK',
      replay: null,
      pointsById: new Map(),
      maxPointTime: null,
      officeFilter: '',
      verifyArmed: false,
    };

    root.innerHTML = `
      <div class="page-head">
        <h2>🛰️ 定位监控</h2>
        <div class="desc">腕表每 5 秒回传 GPS 与设备状态；时间按<b>对象所在司法所时区</b>显示，
          越界/禁行判定由服务端按同一时区重算。连续两点超合理速度的跳变按 GPS 漂移丢弃，不连线、不报警。</div>
      </div>
      <div class="monitor-layout">
        <aside class="card monitor-list-card">
          <div class="card-title">监管对象
            <span class="sub" id="ov-summary">—</span>
          </div>
          ${session.role === 'SUPERVISOR' ? `
            <select class="input" id="ov-office" style="margin:8px 0;width:100%">
              <option value="">全部司法所</option>
            </select>` : ''}
          <div id="ov-list" class="monitor-list"><div class="skeleton">实时总览加载中…</div></div>
        </aside>
        <section class="monitor-main" id="mon-main">
          <div class="state-box"><div class="ico">🧭</div><h3>请选择左侧对象</h3>
            <p>选择一名在矫对象查看实时活动轨迹、电子围栏与禁区、设备状态和在矫完成度。</p></div>
        </section>
      </div>`;

    const listEl = root.querySelector('#ov-list');
    const mainEl = root.querySelector('#mon-main');
    const officeSel = root.querySelector('#ov-office');

    // ---------- 总览（5s 轮询） ----------
    async function loadOverview() {
      try {
        const ov = await Api.get('/monitor/overview');
        state.items = ov.items;
        if (session.role === 'SUPERVISOR' && officeSel && !state.offices.length) {
          const uniq = new Map();
          ov.items.forEach((it) => uniq.set(it.officeId, it.officeName));
          state.offices = [...uniq.entries()].map(([id, name]) => ({ id, name }));
          officeSel.innerHTML = '<option value="">全部司法所</option>'
            + state.offices.map((o) => `<option value="${o.id}">${UI.esc(o.name)}</option>`).join('');
        }
        renderOverviewList();
      } catch (e) {
        // 总览加载失败是独立错误态，不吞成“暂无数据”
        const had = state.items.length > 0;
        listEl.innerHTML = `
          <div class="state-box mini">
            <div class="ico">⚠️</div>
            <h3>实时总览加载失败</h3>
            <p>${UI.esc(e.message || '网络异常')}${had ? '（仍展示上一次数据）' : ''}</p>
            <button class="btn sm primary" id="ov-retry">重新加载</button>
          </div>`;
        listEl.querySelector('#ov-retry').onclick = loadOverview;
      }
    }

    function renderOverviewList() {
      const items = state.items.filter((it) =>
        !state.officeFilter || String(it.officeId) === state.officeFilter);
      const onlineN = state.items.filter((it) => it.linkState === 'ONLINE').length;
      const warnN = state.items.filter((it) => it.linkState === 'STALE' || it.linkState === 'OFFLINE').length;
      root.querySelector('#ov-summary').textContent =
        `${items.length} 人 · 在线 ${onlineN} · 异常 ${warnN}`;

      if (!items.length) {
        listEl.innerHTML = `<div class="state-box mini"><div class="ico">🗂️</div>
          <h3>数据范围内暂无在矫对象</h3><p>在矫/请假/训诫状态的对象才进入实时监控。</p></div>`;
        return;
      }
      listEl.innerHTML = items.map((it) => {
        const meta = LINK_META[it.linkState] || LINK_META.NEVER;
        // 准假外出期间越出活动围栏不算越界（不亮红点），仅禁区仍按异常
        const anomaly = (!it.insideRange || it.forbidden) && !it.leaveAuthorized;
        const battery = it.battery == null ? '—'
          : `<span class="dev-batt ${it.battery < 20 ? 'low' : ''}">🔋${it.battery}%</span>`;
        const bars = it.signal == null ? '' : `📶${'▮'.repeat(it.signal)}${'▯'.repeat(4 - it.signal)}`;
        return `
        <button class="mon-item ${state.selectedId === it.objectId ? 'active' : ''}" data-id="${it.objectId}">
          <div class="mon-item-top">
            <span class="mon-dot ${meta.cls}">${meta.icon}</span>
            <b>${UI.esc(it.maskedName)}</b>
            ${it.leaveAuthorized ? '<span class="badge LEAVE" style="margin-left:auto">准假外出</span>'
              : anomaly ? '<span class="badge red" style="margin-left:auto">越界</span>'
              : `<span class="mon-link ${meta.cls}" style="margin-left:auto">${meta.label}</span>`}
          </div>
          <div class="mon-item-meta">
            <span>${UI.esc(it.correctionNo)}</span>
            <span title="${UI.esc(it.timezone)}">${UI.esc(it.officeName)}</span>
          </div>
          <div class="mon-item-dev">
            <span>${battery}</span><span>${bars}</span>
            <span>${it.worn == null ? '' : (it.worn ? '⌚已佩戴' : '<span class="warn-txt">⚠未佩戴</span>')}</span>
            <span class="mon-age">${UI.fmtAge(it.heartbeatAgeSec)}</span>
          </div>
        </button>`;
      }).join('');
      listEl.querySelectorAll('.mon-item').forEach((el) => {
        el.onclick = () => selectObject(Number(el.dataset.id));
      });
    }

    if (officeSel) officeSel.onchange = () => { state.officeFilter = officeSel.value; renderOverviewList(); };

    // ---------- 选中对象：周/月轨迹 ----------
    async function selectObject(id) {
      state.selectedId = id;
      state.verifyArmed = false;
      renderOverviewList();
      mainEl.innerHTML = `<div class="skeleton">轨迹加载中…</div>`;
      await loadReplay(true);
      restartPoll();
    }

    async function loadReplay(full) {
      if (!state.selectedId) return;
      try {
        let path = `/monitor/objects/${state.selectedId}/tracks?range=${state.range}`;
        if (!full && state.maxPointTime) path += `&since=${encodeURIComponent(state.maxPointTime)}`;
        const r = await Api.get(path);
        state.replay = r;
        if (full) {
          state.pointsById = new Map();
          state.maxPointTime = null;
        }
        (r.points || []).forEach((p) => {
          state.pointsById.set(p.id, p);
          if (!state.maxPointTime || p.pointTime > state.maxPointTime) state.maxPointTime = p.pointTime;
        });
        if (full) {
          renderReplay();
        } else {
          renderLiveMap();   // 5s 增量只刷地图，不整页重绘（避免核实模式被重置、留痕反复加载）
        }
      } catch (e) {
        renderReplayError(e);
      }
    }

    function restartPoll() {
      if (pollTimer) clearInterval(pollTimer);
      pollTimer = setInterval(() => {
        if (!root.isConnected) { clearInterval(pollTimer); clearInterval(overviewTimer); return; }
        if (state.selectedId) loadReplay(false);
      }, POLL_MS);
    }

    function curTz() { return (state.replay && state.replay.timezone) || 'Asia/Shanghai'; }

    function renderReplayError(e) {
      mainEl.innerHTML = `
        <div class="card">
          <div class="state-box">
            <div class="ico">⚠️</div>
            <h3>轨迹数据加载失败</h3>
            <p>${UI.esc(e.message || '网络异常，请稍后重试')}</p>
            <p class="state-sub">监控未中断：设备仍在按 5 秒间隔回传，恢复后可继续查看。</p>
            <button class="btn primary" id="rp-retry">重新加载</button>
          </div>
        </div>`;
      mainEl.querySelector('#rp-retry').onclick = () => loadReplay(true);
    }

    function renderReplay() {
      const r = state.replay;
      const tz = r.timezone || 'Asia/Shanghai';
      const points = [...state.pointsById.values()].sort((a, b) =>
        a.pointTime < b.pointTime ? -1 : a.pointTime > b.pointTime ? 1 : a.id - b.id);

      // ---- 空态二：轨迹已清除（区别于从无轨迹） ----
      if (!points.length && r.lastClear) {
        mainEl.innerHTML = `
          <div class="card">
            ${replayHead(r, tz, 0)}
            <div class="state-box">
              <div class="ico">🧹</div>
              <h3>该对象历史轨迹已清除</h3>
              <p>清除操作全程留痕，清除后设备新回传的定位将从空白开始重新记录。</p>
              <div class="clear-record">
                <div><b>清除时间：</b>${UI.fmtTzFull(r.lastClear.at, tz)}（${UI.esc(tz)}）</div>
                <div><b>操作人：</b>${UI.esc(r.lastClear.operatorName)}</div>
                <div><b>清除点数：</b>${r.lastClear.clearedCount} 个有效点</div>
                <div><b>原因：</b>${UI.esc(r.lastClear.reason)}</div>
              </div>
              <button class="btn" id="rp-refresh">刷新查看新轨迹</button>
            </div>
            ${completionHtml(r.completion)}
          </div>`;
        bindHead();
        mainEl.querySelector('#rp-refresh').onclick = () => loadReplay(true);
        return;
      }

      // ---- 空态三：该对象从无轨迹 ----
      if (!points.length) {
        mainEl.innerHTML = `
          <div class="card">
            ${replayHead(r, tz, 0)}
            <div class="state-box">
              <div class="ico">🛰️</div>
              <h3>该对象暂未回传任何轨迹</h3>
              <p>腕表未激活、未配发，或尚无定位帧通过服务端校验。
                漂移丢弃点不计入轨迹；若设备曾回传但全部被判漂移，请检查设备 GPS 与围栏配置。</p>
              <button class="btn" id="rp-refresh2">刷新</button>
            </div>
            ${completionHtml(r.completion)}
          </div>`;
        bindHead();
        mainEl.querySelector('#rp-refresh2').onclick = () => loadReplay(true);
        return;
      }

      // ---- 正常态 ----
      const last = points[points.length - 1];
      mainEl.innerHTML = `
        <div class="card">
          ${replayHead(r, tz, points.length, last)}
          <div class="map-toolbar">
            <span class="map-hint">🟢活动范围内　<span class="lg-red">●</span>越界落点
              <span class="lg-purple">●</span>禁区段落点　<span class="lg-leave">●</span>准假外出（假期内不算越界）
              ◯离线补传　<span class="lg-drift">⤳</span>GPS漂移已丢弃（窗口内 ${r.driftDiscardedInWindow} 点，不连线）</span>
            <button class="btn sm ${state.verifyArmed ? 'primary' : ''}" id="btn-verify-arm">
              ${state.verifyArmed ? '核实模式：点红色落点核实' : '标记越界落点（二次核实）'}</button>
          </div>
          <div class="map-wrap" id="map-wrap">${drawMap(r, points)}</div>
          <div class="map-caption">
            时间窗（${UI.esc(tz)}）：${UI.fmtTzFull(r.windowFrom, tz)} ～ ${UI.fmtTzFull(r.windowTo, tz)}
            ${r.incremental ? '· 增量更新' : `· 共 ${r.totalAccepted} 个有效点${points.length < r.totalAccepted ? '（首屏已抽稀，越界/禁区点全保留）' : ''}`}
          </div>
        </div>
        ${completionHtml(r.completion)}
        <div class="card">
          <div class="card-title">🧾 监控操作留痕
            <span class="sub">清除轨迹 / 越界核实</span></div>
          <div id="action-log"><div class="skeleton">加载中…</div></div>
        </div>`;
      bindHead();
      bindMap(points, tz);
      loadActions();
    }

    function replayHead(r, tz, count, last) {
      const lastTime = last ? UI.fmtTzFull(last.pointTime, tz) : '—';
      return `
        <div class="rp-head">
          <div class="rp-title">
            <h3 style="font-size:16px">${UI.esc(r.maskedName)}
              <span style="color:var(--ink-muted);font-weight:400;font-size:12.5px">${UI.esc(r.correctionNo)}</span>
            </h3>
            <div class="rp-sub">${UI.esc(tz === 'Asia/Urumqi' ? '伊宁司法所（UTC+6，跨时区协作点）' : '')}
              <span title="IANA 时区">🕓 ${UI.esc(tz)}</span>
              · 最近落点 <b>${lastTime}</b>
            </div>
          </div>
          <div class="rp-controls">
            <div class="seg">
              <button class="seg-btn ${r.range === 'WEEK' ? 'on' : ''}" data-range="WEEK">周视图·7天</button>
              <button class="seg-btn ${r.range === 'MONTH' ? 'on' : ''}" data-range="MONTH">月视图·30天</button>
            </div>
            <button class="btn sm danger" id="btn-clear">🗑 清除轨迹</button>
          </div>
        </div>`;
    }

    function bindHead() {
      const r = state.replay;
      mainEl.querySelectorAll('.seg-btn').forEach((b) => {
        b.onclick = () => { state.range = b.dataset.range; loadReplay(true); };
      });
      const clearBtn = mainEl.querySelector('#btn-clear');
      if (clearBtn) clearBtn.onclick = onClearTracks;
      const arm = mainEl.querySelector('#btn-verify-arm');
      if (arm) arm.onclick = () => {
        state.verifyArmed = !state.verifyArmed;
        arm.classList.toggle('primary', state.verifyArmed);
        arm.textContent = state.verifyArmed ? '核实模式：点红色落点核实' : '标记越界落点（二次核实）';
        UI.toast(state.verifyArmed ? '已进入核实模式：点击地图上越界/禁区落点填写原因' : '已退出核实模式', 'info');
      };
    }

    // ---------- SVG 地图 ----------
    function drawMap(r, points) {
      const W = 860, H = 460, PAD = 42;
      const lats = [], lngs = [];
      points.forEach((p) => { lats.push(p.lat); lngs.push(p.lng); });
      (r.fences || []).forEach((f) => {
        (f.polygon || []).forEach((pt) => { lats.push(pt[0]); lngs.push(pt[1]); });
        if (f.centerLat != null) { lats.push(f.centerLat); lngs.push(f.centerLng); }
      });
      let minLat = Math.min(...lats), maxLat = Math.max(...lats);
      let minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
      const padLat = Math.max((maxLat - minLat) * 0.08, 0.0005);
      const padLng = Math.max((maxLng - minLng) * 0.08, 0.0005);
      minLat -= padLat; maxLat += padLat; minLng -= padLng; maxLng += padLng;
      const X = (lng) => PAD + (lng - minLng) / (maxLng - minLng) * (W - 2 * PAD);
      const Y = (lat) => H - PAD - (lat - minLat) / (maxLat - minLat) * (H - 2 * PAD);

      const fenceSvg = (r.fences || []).map((f) => {
        const kindCls = f.kind === 'ALLOW_RANGE' ? 'fence-allow' : 'fence-forbid';
        if (f.polygon && f.polygon.length >= 3) {
          const pts = f.polygon.map((p) => `${X(p[1]).toFixed(1)},${Y(p[0]).toFixed(1)}`).join(' ');
          const c = f.polygon[0];
          return `
            <g class="${kindCls}">
              <polygon points="${pts}" />
              <text x="${X(c[1])}" y="${Y(c[0]) - 6}" class="fence-label">${UI.esc(f.name)}</text>
            </g>`;
        }
        // 圆形兜底（未配多边形的活动范围）
        const cx = X(f.centerLng), cy = Y(f.centerLat);
        const dLat = f.radiusMeters / 111320;
        const dLng = f.radiusMeters / (111320 * Math.cos(f.centerLat * Math.PI / 180));
        const rx = Math.abs(X(f.centerLng + dLng) - cx);
        const ry = Math.abs(Y(f.centerLat + dLat) - cy);
        return `<g class="${kindCls}"><ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}"/>
          <text x="${cx}" y="${cy - ry - 6}" class="fence-label">${UI.esc(f.name)}（半径${f.radiusMeters}m）</text></g>`;
      }).join('');

      // 轨迹折线（漂移点已在服务端剔除，这里只画 ACCEPTED）
      const linePts = points.map((p) => `${X(p.lng).toFixed(1)},${Y(p.lat).toFixed(1)}`).join(' ');
      const dots = points.map((p, i) => {
        const cls = p.forbidden ? 'pt-forbid' : p.leaveAuthorized ? 'pt-leave'
          : p.outsideFence ? 'pt-out' : 'pt-ok';
        const off = p.offlineCaptured ? 'offline' : '';
        const clickable = (p.outsideFence || p.forbidden) ? 'clickable' : '';
        const verified = p.verified ? 'verified' : '';
        const r0 = (p.outsideFence || p.forbidden) ? 5.5 : p.leaveAuthorized ? 4.5 : 3;
        const whereLabel = p.forbidden ? '禁区段落点' : p.outsideFence ? '越界落点'
          : p.leaveAuthorized ? '准假外出（假期内不算越界）' : '活动范围内';
        return `<circle class="pt ${cls} ${off} ${clickable} ${verified}" data-id="${p.id}"
                  cx="${X(p.lng).toFixed(1)}" cy="${Y(p.lat).toFixed(1)}" r="${r0}">
                  <title>${UI.fmtTzFull(p.pointTime, r.timezone)}（${UI.esc(r.timezone)}）
${whereLabel}${p.offlineCaptured ? '·离线补传' : ''}
电量${p.battery == null ? '—' : p.battery + '%'} 信号${p.signal == null ? '—' : p.signal} ${p.worn === false ? '未佩戴' : ''}
${p.verified ? '已核实：' + UI.esc(p.verifyConclusion || '') : ''}</title>
                </circle>${p.verified
                  ? `<text x="${(X(p.lng) + 6).toFixed(1)}" y="${(Y(p.lat) - 6).toFixed(1)}" class="pt-verify-mark">✓已核实</text>`
                  : ''}`;
      }).join('');

      const last = points[points.length - 1];
      const lastCls = last.forbidden ? 'pt-forbid' : last.leaveAuthorized ? 'pt-leave'
        : last.outsideFence ? 'pt-out' : 'pt-ok';
      const cur = `<g>
        <circle class="cur-pulse" cx="${X(last.lng)}" cy="${Y(last.lat)}" r="8"></circle>
        <circle class="cur-core ${lastCls}"
          cx="${X(last.lng)}" cy="${Y(last.lat)}" r="5"></circle>
        <text x="${X(last.lng) + 10}" y="${Y(last.lat) + 4}" class="cur-label">当前位置${
          last.leaveAuthorized ? '（准假外出）' : ''}</text>
      </g>`;

      return `
        <svg viewBox="0 0 ${W} ${H}" class="map-svg" role="img" aria-label="活动轨迹与电子围栏地图">
          <rect x="0" y="0" width="${W}" height="${H}" class="map-bg"/>
          ${gridSvg(W, H, PAD)}
          ${fenceSvg}
          <polyline class="track-line" points="${linePts}" fill="none"/>
          ${dots}
          ${cur}
        </svg>`;
    }

    function gridSvg(W, H, PAD) {
      let g = '';
      for (let i = 1; i < 6; i++) {
        const x = PAD + (W - 2 * PAD) * i / 6;
        g += `<line x1="${x}" y1="${PAD}" x2="${x}" y2="${H - PAD}" class="grid"/>`;
        const y = PAD + (H - 2 * PAD) * i / 6;
        g += `<line x1="${PAD}" y1="${y}" x2="${W - PAD}" y2="${y}" class="grid"/>`;
      }
      return g;
    }

    function bindMap(points, tz) {
      mainEl.querySelectorAll('circle.pt.clickable').forEach((el) => {
        el.onclick = () => {
          const p = state.pointsById.get(Number(el.dataset.id));
          if (!p) return;
          if (!state.verifyArmed) {
            UI.toast('这是越界/禁区落点：点右上「标记越界落点」进入核实模式后再标记', 'warn');
            return;
          }
          onVerifyPoint(p, tz);
        };
      });
    }

    /** 5s 增量：只重绘地图与点数字，保留核实模式/滚动位置，不重复拉取留痕 */
    function renderLiveMap() {
      const wrap = mainEl.querySelector('#map-wrap');
      if (!wrap) { renderReplay(); return; }  // 当前是空态（如新轨迹刚出现）则回退整绘
      const r = state.replay;
      const points = [...state.pointsById.values()].sort((a, b) =>
        a.pointTime < b.pointTime ? -1 : a.pointTime > b.pointTime ? 1 : a.id - b.id);
      if (!points.length) { renderReplay(); return; }
      wrap.innerHTML = drawMap(r, points);
      bindMap(points, r.timezone);
      const cap = mainEl.querySelector('.map-caption');
      if (cap) {
        cap.innerHTML = `时间窗（${UI.esc(r.timezone)}）：${UI.fmtTzFull(r.windowFrom, r.timezone)} ～ ${UI.fmtTzFull(r.windowTo, r.timezone)}
          · 共 ${state.pointsById.size} 个有效点${state.pointsById.size < r.totalAccepted ? '（首屏已抽稀，越界/禁区点全保留）' : ''}
          · <span style="color:var(--good)">● 实时增量更新 ${UI.fmtTzClock(new Date().toISOString(), r.timezone)}</span>`;
      }
    }

    // ---------- 越界落点二次核实（必填原因留痕，服务端重算） ----------
    async function onVerifyPoint(p, tz) {
      let chosenConc = 'REALLY_BREACH';
      const conclusionOptions = CONCLUSIONS.map((c, i) =>
        `<label class="radio-opt ${i === 0 ? 'sel' : ''}">
           <input type="radio" name="vf-conc" value="${c.v}" ${i === 0 ? 'checked' : ''}
             onchange="window.__vfChoose(this.value);this.closest('.radio-group').querySelectorAll('.radio-opt').forEach(function(el){el.classList.remove('sel');});this.closest('.radio-opt').classList.add('sel');"/> ${c.label}
         </label>`).join('');
      global.__vfChoose = (v) => { chosenConc = v; };
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '越界落点二次核实',
          icon: '📍',
          warn: '服务端会用当前电子围栏几何与该所时区对该落点重新计算，前端标记不作数；本次核实结论与原因将留痕。',
          bodyHtml: `落点时间（${UI.esc(tz)}）：<b>${UI.fmtTzFull(p.pointTime, tz)}</b><br/>
            类型：${p.forbidden ? '<span class="badge red">禁区段落点</span>' : '<span class="badge red">越界落点</span>'}
            ${p.offlineCaptured ? '（离线补传点）' : ''}
            <div class="radio-group" id="vf-group">${conclusionOptions}</div>`,
          reasonLabel: '核实原因/处置说明',
          reasonPlaceholder: '如：电话核查对象在县医院陪护，已批准请假，现场无越界故意…',
          requireReason: true,
          confirmText: '提交核实并留痕',
        });
      } catch { return; }
      const conc = chosenConc;
      try {
        await Api.post(`/monitor/objects/${state.selectedId}/points/${p.id}/verify`,
          { conclusion: conc, reason });
        p.verified = true;
        p.verifyConclusion = conc;
        UI.toast('已提交核实并留痕（服务端已重算该落点）', 'success');
        renderReplay();
      } catch (e) {
        if (e.code === 'POINT_NOT_ANOMALY') {
          UI.alertModal('服务端重算未发现越界',
            `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
        } else UI.toast(e.message, 'error');
      }
    }

    // ---------- 清除轨迹（二次确认 + 必填原因留痕） ----------
    async function onClearTracks() {
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '清除该对象全部轨迹',
          icon: '🧹',
          warn: '高危操作：将删除该对象全部有效轨迹与漂移记录并清空实时位置，操作人、原因、时间、点数将留痕，且不可恢复。',
          bodyHtml: `对象 <span class="target-name">${UI.esc(state.replay.maskedName)}（${UI.esc(state.replay.correctionNo)}）</span>，
            清除后监控页对其显示“轨迹已清除”空态，而不是“暂无数据”。`,
          reasonLabel: '清除原因',
          reasonPlaceholder: '如：设备回收更换、误测数据清洗、依法归档清除…',
          requireReason: true,
          confirmText: '确认清除并留痕',
          danger: true,
        });
      } catch { return; }
      try {
        await Api.post(`/monitor/objects/${state.selectedId}/clear-tracks`, { reason });
        state.pointsById = new Map();
        state.maxPointTime = null;
        UI.toast('轨迹已清除并留痕', 'success');
        await loadReplay(true);
        loadOverview();
      } catch (e) { UI.toast(e.message, 'error'); }
    }

    async function loadActions() {
      const slot = mainEl.querySelector('#action-log');
      if (!slot) return;
      try {
        const logs = await Api.get(`/monitor/objects/${state.selectedId}/actions`);
        if (!logs.length) { slot.innerHTML = '<div style="color:var(--ink-muted);font-size:13px">暂无清除/核实留痕</div>'; return; }
        slot.innerHTML = `<div class="violation-list">${logs.map((a) => `
          <div class="violation-item">
            <span style="margin-top:2px">${a.action === 'CLEAR_TRACKS' ? '🧹' : '📍'}</span>
            <div class="v-body">
              <div class="v-detail">
                <span class="badge ${a.action === 'CLEAR_TRACKS' ? 'gray' : 'red'}">
                  ${a.action === 'CLEAR_TRACKS' ? '清除轨迹' : '落点核实'}</span>
                ${a.conclusion ? '结论：' + UI.esc(concLabel(a.conclusion)) + '　' : ''}${UI.esc(a.reason)}
              </div>
              <div class="v-meta">${UI.esc(a.operatorName)} · ${UI.fmtTzFull(a.createdAt, curTz())}
                ${a.detail ? '· ' + UI.esc(a.detail) : ''}</div>
            </div>
          </div>`).join('')}</div>`;
      } catch { slot.innerHTML = '<div style="color:var(--ink-muted);font-size:13px">留痕加载失败，不影响轨迹查看</div>'; }
    }

    function concLabel(v) {
      return (CONCLUSIONS.find((c) => c.v === v) || {}).label || v;
    }

    // ---------- 完成度双口径 ----------
    function completionHtml(c) {
      if (!c) return '';
      const pct = (x) => Math.round(x * 100) + '%';
      return `
        <div class="card completion-card">
          <div class="card-title">📊 在矫完成度
            <span class="sub">统计窗口（${UI.esc(c.timezone)}）${UI.esc(c.basisFrom)} ～ ${UI.esc(c.basisTo)} · 近 30 天</span>
          </div>
          ${c.opposite ? `
            <div class="opposite-warn">⚠ 两种口径结论相反：该对象“打卡勤”与“报到节点完成”不一致，
              只报一个百分数会误导处置——请同时看下方两口径，重点核查关键报到节点是否漏报到。</div>` : ''}
          <div class="completion-grid">
            <div class="completion-tile">
              <div class="ct-label">口径一 · 打卡天数
                <span class="ct-help" title="${UI.esc(c.checkinDayDefinition)}">ⓘ</span></div>
              <div class="ct-num">${pct(c.checkinDayRate)}</div>
              <div class="bar-track"><div class="bar-fill day" style="width:${pct(c.checkinDayRate)}"></div></div>
              <div class="ct-frac">实际打卡 <b>${c.actualCheckinDays}</b> 天 / 自然日 ${c.calendarDays} 天</div>
              <div class="ct-def">${UI.esc(c.checkinDayDefinition)}</div>
            </div>
            <div class="completion-tile">
              <div class="ct-label">口径二 · 关键报到
                <span class="ct-help" title="${UI.esc(c.keyReportDefinition)}">ⓘ</span></div>
              <div class="ct-num">${pct(c.keyReportRate)}</div>
              <div class="bar-track"><div class="bar-fill key" style="width:${pct(c.keyReportRate)}"></div></div>
              <div class="ct-frac">报到节点完成 <b>${c.keyReportDone}</b> 次 / 应到 ${c.keyReportDue} 次</div>
              <div class="ct-def">${UI.esc(c.keyReportDefinition)}</div>
            </div>
          </div>
        </div>`;
    }

    loadOverview();
    overviewTimer = setInterval(() => {
      if (!root.isConnected) { clearInterval(overviewTimer); clearInterval(pollTimer); return; }
      loadOverview();
    }, POLL_MS);
    if (state.selectedId) selectObject(state.selectedId);
  };
})(window);
