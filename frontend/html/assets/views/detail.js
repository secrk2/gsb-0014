/* 对象档案详情：实名二次确认、状态机流转、留痕时间线、轨迹回放 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  // 与后端状态机保持一致的合法去向（后端为最终裁决，前端仅用于渲染按钮）。
  // 注意：进入/离开「请假外出」一律走左侧「请销假审批」两级流程（司法所初审→区局复核），
  // 不在档案页直接一键切换，以免绕过审批与定位联动、逾期巡检；逾假训诫由系统按假期自动升级。
  const NEXT = {
    INTAKE: [{ to: 'SERVING', label: '办理入矫宣告', needReason: false }],
    SERVING: [
      { to: 'ADMONISHED', label: '予以训诫', needReason: true, danger: true },
      { to: 'REIMPRISONED', label: '提请收监', needReason: true, danger: true },
      { to: 'RELEASED', label: '解除矫正', needReason: true },
    ],
    LEAVE: [],   // 销假/逾假训诫请在「请销假审批」办理
    ADMONISHED: [
      { to: 'SERVING', label: '教育改正·恢复在矫', needReason: false },
      { to: 'REIMPRISONED', label: '情节严重·收监', needReason: true, danger: true },
    ],
    REIMPRISONED: [],
    RELEASED: [],
  };
  const EMPTY_HINT = {
    LEAVE: '对象处于「请假外出」。请在左侧「请销假审批」中办理销假返所；'
      + '假期结束未销假的，系统会自动升为逾假违规（训诫），不在档案页直接切换。',
  };

  Views.detail = async function (root, id) {
    root.innerHTML = `<div class="skeleton">档案加载中…</div>`;

    let detail;
    try {
      detail = await Api.get('/objects/' + id);
    } catch (e) {
      if (e.code === 'FORBIDDEN' || e.status === 403) {
        root.innerHTML = forbiddenHtml(e.message);
        root.querySelector('#btn-back').onclick = () => { location.hash = '#/objects'; };
        return;
      }
      if (e.code === 'NOT_FOUND' || e.status === 404) {
        root.innerHTML = stateHtml('🧭', '档案不存在', e.message);
        return;
      }
      root.innerHTML = stateHtml('⚠️', '档案加载失败', e.message);
      return;
    }

    const o = detail.object;
    render();

    function render() {
      const actions = NEXT[o.status] || [];
      root.innerHTML = `
        <div class="page-head" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <button class="btn sm" id="btn-back">← 返回列表</button>
          <h2 style="margin:0">${UI.esc(o.maskedName)}</h2>
          <span style="color:var(--ink-muted);font-size:13px">${UI.esc(o.correctionNo)}</span>
          ${UI.statusBadge(o.status)}
          <span style="flex:1"></span>
          <span style="font-size:12.5px;color:var(--ink-muted)">${UI.esc(o.officeName)}</span>
        </div>

        <div class="detail-grid">
          <div>
            <div class="card">
              <div class="card-title">📄 档案信息
                <span class="sub">实名默认脱敏隐藏</span>
              </div>
              <dl class="kv">
                <dt>真实姓名</dt>
                <dd>
                  <span class="name-secret">
                    <span class="secret" id="btn-reveal" title="查看需二次确认并填写理由">●●● 点击申请查看</span>
                    <span id="fullname-slot" style="font-weight:700;display:none"></span>
                  </span>
                </dd>
                <dt>罪名</dt><dd>${UI.esc(o.charge || '—')}</dd>
                <dt>证件尾号</dt><dd>${UI.esc(o.idCardTail || '—')}</dd>
                <dt>联系电话</dt><dd>${UI.esc(o.phone || '—')}</dd>
                <dt>矫正期限</dt><dd>${UI.esc(o.startDate || '—')} 至 ${UI.esc(o.endDate || '—')}</dd>
                <dt>规定报到</dt><dd>每${UI.WEEK_LABEL[o.reportDay] || '—'}</dd>
                <dt>今日报到</dt>
                <dd>${detail.checkedToday
                  ? '<span class="badge green">已报到</span>'
                  : '<span class="badge gray">未报到</span>'}</dd>
                <dt>最近定位</dt>
                <dd>${o.lastLocationAt
                  ? `${UI.fmtTzFull(o.lastLocationAt, o.timezone)}（${UI.esc(o.timezone)}） · ` +
                    (o.lastLeaveAuthorized ? '<span class="badge LEAVE">准假外出</span>'
                      : o.lastInsideFence ? '<span class="badge green">围栏内</span>' : '<span class="badge red">📍越界</span>')
                    + (o.lastForbidden ? ' <span class="badge red">禁区</span>' : '')
                  : '暂无'}</dd>
              </dl>
              <div id="audit-slot"></div>
            </div>

            <div class="card">
              <div class="card-title">🧭 定位轨迹
                <span class="sub">共 ${detail.trackCount} 个有效点（重复补传点不计入）</span>
              </div>
              <div id="tracks-slot"><div class="skeleton">轨迹加载中…</div></div>
            </div>
          </div>

          <div>
            <div class="card">
              <div class="card-title">🔄 状态流转</div>
              ${actions.length ? `
                <div style="font-size:13px;color:var(--ink-secondary);margin-bottom:6px">
                  当前「${UI.STATUS_LABEL[o.status]}」，可执行：
                </div>
                <div class="transition-pad">
                  ${actions.map((a) => `
                    <button class="btn ${a.danger ? 'danger' : 'primary'} sm" data-action="${a.to}"
                      data-need-reason="${a.needReason}">${a.label}</button>`).join('')}
                </div>` : `
                <div class="confirm-warn">${EMPTY_HINT[o.status]
                  || ('该状态为终态（' + UI.STATUS_LABEL[o.status] + '），状态机不允许任何回退或跳转。')}</div>`}
              <div id="transition-result" style="margin-top:12px"></div>
            </div>

            <div class="card">
              <div class="card-title">🧾 流转留痕</div>
              <div class="timeline">
                ${detail.transitions.length ? detail.transitions.map((t) => `
                  <div class="tl-item">
                    <div class="tl-line">
                      <b>${UI.esc(t.fromStatusLabel)}</b> → <b>${UI.esc(t.toStatusLabel)}</b>
                    </div>
                    <div class="tl-meta">
                      ${UI.esc(t.operatorName)} · ${UI.fmtTzFull(t.operatedAt, o.timezone)}
                      ${t.reason ? '· ' + UI.esc(t.reason) : ''}
                    </div>
                  </div>`).join('') : '<div style="color:var(--ink-muted);font-size:13px">暂无流转记录</div>'}
              </div>
            </div>

            <div class="card">
              <div class="card-title">🚨 违规与越界记录</div>
              <div class="violation-list">
                ${detail.violations.length ? detail.violations.map((v) => `
                  <div class="violation-item">
                    <span class="red-dot" style="margin-top:6px"></span>
                    <div class="v-body">
                      <div class="v-detail"><span class="badge red" style="margin-right:6px">${UI.esc(v.typeLabel)}</span>${UI.esc(v.detail)}</div>
                      <div class="v-meta">${UI.fmtTzFull(v.eventTime, o.timezone)}（${UI.esc(o.timezone)}）</div>
                    </div>
                  </div>`).join('')
                  : '<div style="color:var(--good);font-size:13px">暂无违规记录</div>'}
              </div>
            </div>
          </div>
        </div>`;

      root.querySelector('#btn-back').onclick = () => { location.hash = '#/objects'; };
      root.querySelector('#btn-reveal').onclick = onReveal;
      root.querySelectorAll('[data-action]').forEach((btn) => {
        btn.onclick = () => onTransition(btn.dataset.action, btn.textContent.trim(),
          btn.dataset.needReason === 'true');
      });
      loadTracks();
      loadAudits();
    }

    async function onReveal() {
      let reason;
      try {
        reason = await UI.confirmModal({
          title: '查看矫正对象全名',
          icon: '🔐',
          warn: '对象姓名属敏感信息，本次查看将记录查看人、理由与时间，接受事后审计。',
          bodyHtml: `即将查看 <span class="target-name">${UI.esc(o.maskedName)}（${UI.esc(o.correctionNo)}）</span> 的真实姓名。`,
          reasonLabel: '查看理由',
          reasonPlaceholder: '司法所核查、训诫谈话、应急处置等工作需要',
          requireReason: true,
          confirmText: '确认查看并留痕',
        });
      } catch { return; }

      try {
        const res = await Api.post('/objects/' + o.id + '/reveal-name', { reason });
        root.querySelector('#btn-reveal').style.display = 'none';
        const slot = root.querySelector('#fullname-slot');
        slot.style.display = 'inline';
        slot.textContent = res.fullName;
        UI.toast('全名已展示，查看行为已留痕', 'success');
        loadAudits();
      } catch (e) {
        UI.toast(e.message, 'error');
      }
    }

    async function loadAudits() {
      const slot = root.querySelector('#audit-slot');
      if (!slot) return;
      try {
        const audits = await Api.get('/objects/' + o.id + '/name-audits');
        if (!audits.length) { slot.innerHTML = ''; return; }
        slot.innerHTML = `
          <div style="margin-top:14px;border-top:1px solid var(--hairline);padding-top:10px">
            <div style="font-size:12.5px;font-weight:600;color:var(--ink-secondary);margin-bottom:6px">
              全名查看留痕（最近 ${audits.length} 次）
            </div>
            <div class="violation-list">
              ${audits.slice(0, 5).map((a) => `
                <div class="violation-item">
                  <span style="margin-top:2px">👁</span>
                  <div class="v-body">
                    <div class="v-detail">${UI.esc(a.viewerName)}：${UI.esc(a.reason)}</div>
                    <div class="v-meta">${UI.fmtTzFull(a.viewedAt, o.timezone)}</div>
                  </div>
                </div>`).join('')}
            </div>
          </div>`;
      } catch { /* 留痕记录加载失败不阻塞主档 */ }
    }

    async function onTransition(target, label, needReason) {
      let reason = null;
      try {
        reason = await UI.confirmModal({
          title: '确认状态变更：' + label,
          icon: '🔄',
          warn: '状态流转受状态机约束，非法回退会被系统拒绝并记录；该操作将写入流转留痕。',
          bodyHtml: `对象 <span class="target-name">${UI.esc(o.maskedName)}</span>：
            「${UI.STATUS_LABEL[o.status]}」→「${UI.STATUS_LABEL[target]}」`,
          requireReason: needReason,
          reasonLabel: '变更理由',
          reasonPlaceholder: '如：请假至县医院陪护，9 月 20 日前销假',
          confirmText: '确认执行',
          danger: ['ADMONISHED', 'REIMPRISONED'].includes(target),
        });
      } catch { return; }

      try {
        await Api.post('/objects/' + o.id + '/transition', { targetStatus: target, reason });
        UI.toast('状态已变更为「' + UI.STATUS_LABEL[target] + '」', 'success');
        const fresh = await Api.get('/objects/' + id);
        detail = fresh;
        Object.assign(o, fresh.object);
        render();
      } catch (e) {
        if (e.code === 'INVALID_TRANSITION') {
          UI.alertModal('状态机已拦截该变更',
            `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
        } else {
          UI.toast(e.message, 'error');
        }
      }
    }

    async function loadTracks() {
      const slot = root.querySelector('#tracks-slot');
      try {
        const tracks = await Api.get('/objects/' + o.id + '/tracks');
        if (!tracks.length) {
          slot.innerHTML = '<div style="color:var(--ink-muted);font-size:13px">暂无轨迹点</div>';
          return;
        }
        const shown = tracks.slice(-12).reverse();
        slot.innerHTML = `
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>采集时间</th><th>坐标</th><th>来源</th><th>围栏</th></tr></thead>
              <tbody>
                ${shown.map((t) => `
                  <tr>
                    <td data-label="采集时间" style="white-space:nowrap">${UI.fmtTzFull(t.pointTime, o.timezone)}</td>
                    <td data-label="坐标" style="font-variant-numeric:tabular-nums;font-size:12.5px">
                      ${t.lat.toFixed(5)}, ${t.lng.toFixed(5)}</td>
                    <td data-label="来源">${t.offlineCaptured
                      ? '<span class="badge" style="color:var(--warning);background:var(--warning-bg)">离线补传</span>'
                      : '<span class="badge green">实时</span>'}</td>
                    <td data-label="围栏">${t.outsideFence
                      ? '<span class="badge red">越界</span>'
                      : '<span class="badge green">内</span>'}</td>
                  </tr>`).join('')}
              </tbody>
            </table>
          </div>
          ${tracks.length > 12 ? `<div style="font-size:12px;color:var(--ink-muted);margin-top:6px">仅显示最近 12 个点</div>` : ''}`;
      } catch (e) {
        slot.innerHTML = `<div style="color:var(--critical);font-size:13px">轨迹加载失败：${UI.esc(e.message)}</div>`;
      }
    }
  };

  function forbiddenHtml(message) {
    return `
      <div class="state-box forbidden">
        <div class="ico">🚫</div>
        <h3>无权访问该档案</h3>
        <p>${UI.esc(message)}</p>
        <p style="color:var(--ink-muted)">矫正对象之间数据相互隔离，每位对象只能查看本人档案；
          司法所干警只能查看本所对象。本次越权尝试已在服务端被拒绝。</p>
        <button class="btn primary" id="btn-back">返回档案列表</button>
      </div>`;
  }

  function stateHtml(icon, title, message) {
    return `<div class="state-box"><div class="ico">${icon}</div>
      <h3>${UI.esc(title)}</h3><p>${UI.esc(message || '')}</p></div>`;
  }
})(window);
