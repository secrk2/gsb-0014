/* 对象档案列表 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  Views.objects = async function (root) {
    const session = Api.getSession();
    root.innerHTML = `<div class="skeleton">档案加载中…</div>`;

    // 监管员可按司法所筛选：从作战台取所列表
    let offices = [];
    if (session.role === 'SUPERVISOR') {
      try {
        const dash = await Api.get('/dashboard');
        offices = dash.offices;
      } catch { /* 筛选可选，失败不阻塞 */ }
    }

    let statusFilter = '';
    let officeFilter = '';

    async function load() {
      const params = new URLSearchParams();
      if (statusFilter) params.set('status', statusFilter);
      if (officeFilter) params.set('officeId', officeFilter);
      listRoot.innerHTML = `<div class="skeleton">加载中…</div>`;
      try {
        const items = await Api.get('/objects' + (params.toString() ? '?' + params : ''));
        renderTable(items);
      } catch (e) {
        listRoot.innerHTML = ErrorState('档案加载失败', e.message);
      }
    }

    root.innerHTML = `
      <div class="page-head">
        <h2>🗂️ 对象档案</h2>
        <div class="desc">列表按规定脱敏显示（姓氏首字母-编号）；查看全名须二次确认并填写理由，全程留痕</div>
      </div>
      <div class="card">
        <div class="filter-bar">
          <label style="font-size:13px;color:var(--ink-secondary)">状态</label>
          <select class="input" id="f-status">
            <option value="">全部状态</option>
            ${Object.entries(UI.STATUS_LABEL).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}
          </select>
          ${offices.length ? `
            <label style="font-size:13px;color:var(--ink-secondary)">司法所</label>
            <select class="input" id="f-office">
              <option value="">全部司法所</option>
              ${offices.map((o) => `<option value="${o.officeId}">${UI.esc(o.officeName)}</option>`).join('')}
            </select>` : ''}
          <span style="flex:1"></span>
          <span style="font-size:12.5px;color:var(--ink-muted)">数据范围：${
            session.role === 'SUPERVISOR' ? '全区所有司法所' : '仅本所（' + UI.esc(session.officeName || '') + '）'}</span>
        </div>
        <div class="table-wrap" id="list-root"></div>
      </div>`;

    const listRoot = root.querySelector('#list-root');
    root.querySelector('#f-status').onchange = (e) => { statusFilter = e.target.value; load(); };
    const officeSel = root.querySelector('#f-office');
    if (officeSel) officeSel.onchange = (e) => { officeFilter = e.target.value; load(); };

    function renderTable(items) {
      if (!items.length) {
        listRoot.innerHTML = `<div class="state-box"><div class="ico">🗂️</div><h3>暂无符合条件的档案</h3>
          <p>可调整上方状态/司法所筛选条件。</p></div>`;
        return;
      }
      const showOffice = session.role === 'SUPERVISOR';
      listRoot.innerHTML = `
        <table class="data">
          <thead><tr>
            <th>矫正编号</th><th>脱敏姓名</th>${showOffice ? '<th>司法所</th>' : ''}
            <th>状态</th><th>罪名</th><th>报到日</th><th>最近定位</th><th></th>
          </tr></thead>
          <tbody>
            ${items.map((o) => {
              const loc = o.lastLocationAt
                ? `${UI.fmtTzFull(o.lastLocationAt, o.timezone)} ` +
                  (o.lastLeaveAuthorized
                    ? '<span class="badge LEAVE" style="margin-left:4px">准假外出</span>'
                    : o.lastInsideFence
                    ? '<span class="badge green" style="margin-left:4px">围栏内</span>'
                    : '<span class="badge red" style="margin-left:4px">📍越界</span>')
                    + (o.lastForbidden ? '<span class="badge red" style="margin-left:4px">禁区</span>' : '')
                : '<span style="color:var(--ink-muted)">无定位</span>';
              return `
              <tr class="clickable" data-id="${o.id}">
                <td data-label="矫正编号">${UI.esc(o.correctionNo)}</td>
                <td data-label="脱敏姓名"><b>${UI.esc(o.maskedName)}</b></td>
                ${showOffice ? `<td data-label="司法所">${UI.esc(o.officeName)}</td>` : ''}
                <td data-label="状态">${UI.statusBadge(o.status)}</td>
                <td data-label="罪名">${UI.esc(o.charge || '—')}</td>
                <td data-label="报到日">${UI.WEEK_LABEL[o.reportDay] || '—'}</td>
                <td data-label="最近定位">${loc}</td>
                <td data-label="操作"><button class="btn sm" data-btn="view">查看档案</button></td>
              </tr>`;
            }).join('')}
          </tbody>
        </table>`;
      listRoot.querySelectorAll('tr[data-id]').forEach((tr) => {
        tr.onclick = () => { location.hash = '#/objects/' + tr.dataset.id; };
      });
    }

    await load();
  };
})(window);
