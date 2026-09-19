/* 矫务作战台 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  // 状态 → 语义色（与 CSS badge 同源；颜色 + 段内数字 + 图例文字 + 图标多重编码）
  // 中性态用灰；在矫绿/请假蓝 拉开红绿色盲差异；训诫亮橙/收监深红 靠明度区分
  const SEG_COLOR = {
    INTAKE: '#8a8a85',
    SERVING: '#0ca30c',
    LEAVE: '#2a78d6',
    ADMONISHED: '#e07016',
    REIMPRISONED: '#a8231a',
    RELEASED: '#9aa0a8',
  };
  const STAGES = [
    ['INTAKE', '入矫登记'], ['SERVING', '在矫'], ['LEAVE', '请假外出'],
    ['ADMONISHED', '训诫'], ['REIMPRISONED', '收监'], ['RELEASED', '解除'],
  ];

  Views.dashboard = async function (root) {
    root.innerHTML = `<div class="skeleton">作战数据加载中…</div>`;
    const d = await Api.get('/dashboard');

    const maxGlobal = Math.max(1, ...STAGES.map(([k]) => Number(d.globalFunnel[k] || 0)));
    const activeTotal = (d.globalFunnel.SERVING || 0) + (d.globalFunnel.LEAVE || 0) + (d.globalFunnel.ADMONISHED || 0);

    root.innerHTML = `
      <div class="page-head">
        <h2>🎯 矫务作战台</h2>
        <div class="desc">UTC 当前时间 ${UI.esc(d.utcToday)}（各条目按对象所在司法所时区判定“今天”） · 在矫口径 = 在矫 + 请假外出 + 训诫（监外执行中）</div>
      </div>

      <div class="stat-row">
        <div class="stat-tile good">
          <div class="lab">监外执行中总数</div>
          <div class="num">${activeTotal}</div>
        </div>
        <div class="stat-tile">
          <div class="lab">纳入建档对象</div>
          <div class="num">${STAGES.reduce((s, [k]) => s + Number(d.globalFunnel[k] || 0), 0)}</div>
        </div>
        <div class="stat-tile warn">
          <div class="lab">今日应报到</div>
          <div class="num">${d.todayDue.length}</div>
        </div>
        <div class="stat-tile critical">
          <div class="lab">未处置预警红点</div>
          <div class="num">${d.redDotTotal}</div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">全区在矫漏斗
          <span class="sub">按矫正状态分布（人）</span></div>
        <div class="funnel-global">
          ${STAGES.map(([k, label]) => {
            const n = Number(d.globalFunnel[k] || 0);
            const w = Math.max(4, Math.round((n / maxGlobal) * 100));
            return `
              <div class="funnel-stage">
                <div class="num">${n}</div>
                <div class="lab">${UI.statusIcon(k)} ${label}</div>
                <div class="bar" style="width:${w}%;background:${SEG_COLOR[k]}"></div>
              </div>`;
          }).join('')}
        </div>
      </div>

      <div class="card">
        <div class="card-title">各司法所漏斗
          <span class="sub">分段条：入矫 / 在矫 / 请假 / 训诫 / 收监 / 解除（悬停看明细）</span>
        </div>
        <div class="office-grid">
          ${d.offices.map(officeCard).join('') || emptyP('数据范围内暂无司法所')}
        </div>
      </div>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px" class="below-grid">
        <div class="card" style="margin-top:0">
          <div class="card-title">📋 今日应报到
            <span class="sub">按对象规定报到星期匹配</span>
          </div>
          <div class="due-list">
            ${d.todayDue.length ? d.todayDue.map(dueItem).join('') : emptyP('今日没有应报到对象')}
          </div>
        </div>
        <div class="card" style="margin-top:0">
          <div class="card-title">🔴 越界与违规红点
            <span class="sub">未处置 ${d.redDotTotal} 条</span>
          </div>
          <div class="violation-list">
            ${d.redDots.length ? d.redDots.map(redItem).join('') : emptyP('<b style="color:var(--good)">暂无未处置预警</b>')}
          </div>
        </div>
      </div>`;

    root.querySelectorAll('[data-object-id]').forEach((el) => {
      el.style.cursor = 'pointer';
      el.onclick = () => { location.hash = '#/objects/' + el.dataset.objectId; };
    });
  };

  function officeCard(o) {
    const segs = [
      ['INTAKE', o.intake], ['SERVING', o.serving], ['LEAVE', o.leave],
      ['ADMONISHED', o.admonished], ['REIMPRISONED', o.reimprisoned], ['RELEASED', o.released],
    ].filter(([, n]) => n > 0);
    const total = segs.reduce((s, [, n]) => s + n, 0) || 1;
    return `
      <div class="office-card">
        <div class="oc-head">
          <span class="oc-name">${UI.esc(o.officeName)}</span>
          <span class="oc-region">${UI.esc(o.region || '')}</span>
          <span class="oc-active">在矫 <b>${o.activeTotal}</b></span>
        </div>
        <div class="oc-bar" title="在矫漏斗，共 ${total} 人">
          ${segs.map(([k, n]) => {
            const pct = ((n / total) * 100).toFixed(1);
            return `<i style="width:${pct}%;background:${SEG_COLOR[k]}" title="${UI.STATUS_LABEL[k]} ${n} 人"><span>${n}</span></i>`;
          }).join('')}
        </div>
        <div class="oc-legend">
          ${STAGES.map(([k, label]) => `<span><i class="dot" style="background:${SEG_COLOR[k]}"></i>${label}</span>`).join('')}
        </div>
      </div>`;
  }

  function dueItem(it) {
    const state = it.checkedToday
      ? '<span class="badge green"><span class="b-ico">✅</span>已报到</span>'
      : (it.overdue
          ? '<span class="badge red"><span class="b-ico">⛔</span>逾时未报</span>'
          : '<span class="badge gray"><span class="b-ico">🕒</span>待报到</span>');
    return `
      <div class="due-item" data-object-id="${it.objectId}">
        <span class="red-dot" style="${it.checkedToday ? 'visibility:hidden' : ''}"></span>
        <div style="flex:1;min-width:0">
          <div><b>${UI.esc(it.maskedName)}</b> <span style="color:var(--ink-muted);font-size:12px">${UI.esc(it.correctionNo)}</span></div>
          <div style="font-size:12px;color:var(--ink-muted)">${UI.esc(it.officeName)} · 规定 ${UI.WEEK_LABEL[it.reportDay] || it.reportDay} 报到 · 当地日 ${UI.esc(it.localDate)}</div>
        </div>
        ${state}
      </div>`;
  }

  function redItem(v) {
    const icon = v.type === 'GEOFENCE_BREACH' ? '📍' : v.type === 'ABSENT' ? '🚨' : '⚠️';
    return `
      <div class="violation-item" data-object-id="${v.objectId}">
        <span class="red-dot" style="margin-top:6px"></span>
        <div class="v-body">
          <div class="v-detail">${icon} <span class="badge red" style="margin-right:6px">${UI.esc(v.typeLabel)}</span>${UI.esc(v.detail)}</div>
          <div class="v-meta">${UI.esc(v.maskedName)} · ${UI.esc(v.correctionNo)} · ${UI.esc(v.officeName)} · ${UI.fmtTzFull(v.eventTime, v.timezone)}（${UI.esc(v.timezone)}）</div>
        </div>
      </div>`;
  }

  function emptyP(html) {
    return `<div style="padding:18px 0;color:var(--ink-muted);font-size:13px;text-align:center">${html}</div>`;
  }
})(window);
