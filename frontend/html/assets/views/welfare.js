/* 公益活动（发布/花名册/异常打卡）+ 月度报到批量登记 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  Views.welfare = async function (root) {
    const session = Api.getSession();
    const isSuper = session.role === 'SUPERVISOR';
    let tab = 'ACT';

    root.innerHTML = `
      <div class="page-head">
        <h2>🤝 公益活动与月度报到</h2>
        <div class="desc">对象手机端报名公益活动，现场打卡由服务端核验是否落在活动点范围内，范围外如实标异常；
          月度当面报到支持一次勾选多名对象批量登记，部分失败逐条说明。</div>
      </div>
      <div class="card">
        <div class="filter-bar">
          <button class="btn sm primary" data-tab="ACT">公益活动</button>
          <button class="btn sm" data-tab="MONTH">月度报到批量登记</button>
        </div>
        <div id="welfare-body"><div class="skeleton">加载中…</div></div>
      </div>`;

    root.querySelectorAll('[data-tab]').forEach((b) => {
      b.onclick = () => {
        tab = b.dataset.tab;
        root.querySelectorAll('[data-tab]').forEach((x) =>
          x.classList.toggle('primary', x.dataset.tab === tab));
        if (tab === 'ACT') renderActivities(); else renderMonthly();
      };
    });

    // ============================ 公益活动 ============================

    async function renderActivities() {
      const body = root.querySelector('#welfare-body');
      body.innerHTML = '<div class="skeleton">活动加载中…</div>';
      let offices = [];
      if (isSuper) {
        try { offices = (await Api.get('/dashboard')).offices; } catch { /* ignore */ }
      }
      let activities;
      try {
        activities = await Api.get('/activities');
      } catch (e) {
        body.innerHTML = ErrorState('活动加载失败', e.message);
        return;
      }

      body.innerHTML = `
        <div class="filter-bar">
          <span style="font-size:12.5px;color:var(--ink-muted)">共 ${activities.length} 场活动（数据范围：${
            isSuper ? '全区' : '仅本所'}）</span>
          <span style="flex:1"></span>
          <button class="btn sm primary" id="btn-new-act">＋ 发布公益活动</button>
        </div>
        <div id="act-list"></div>`;
      root.querySelector('#btn-new-act').onclick = () => publishModal(offices);

      const list = root.querySelector('#act-list');
      if (!activities.length) {
        list.innerHTML = `<div class="state-box"><div class="ico">🤝</div><h3>暂未发布公益活动</h3>
          <p>点右上角「发布公益活动」，设置活动点坐标与核验半径后对象即可在手机端报名。</p></div>`;
        return;
      }
      list.innerHTML = activities.map(activityCard).join('');
      list.querySelectorAll('[data-roster]').forEach((b) => {
        b.onclick = () => rosterModal(Number(b.dataset.roster));
      });
      list.querySelectorAll('[data-cancel]').forEach((b) => {
        b.onclick = () => cancelActivity(Number(b.dataset.cancel));
      });
    }

    function activityCard(a) {
      const statusBadge = a.status === 'PUBLISHED'
        ? '<span class="badge green">已发布</span>'
        : a.status === 'CANCELLED'
          ? '<span class="badge red">已取消</span>'
          : '<span class="badge gray">已结束</span>';
      const full = a.full ? '<span class="badge red">已满员</span>' : '';
      return `
      <div class="leave-card">
        <div class="leave-head">
          <b class="leave-title">${UI.esc(a.title)}</b>
          ${statusBadge}${full}
          <span style="flex:1"></span>
          <span class="leave-meta">${UI.esc(a.officeName)} · 已报名 ${a.enrolledCount}${
            a.capacity ? '/' + a.capacity : ''} 人</span>
        </div>
        <div class="leave-body">
          <div class="leave-reason"><b>地点：</b>${UI.esc(a.address)}
            <span style="color:var(--ink-muted)">（核验半径 ${a.radiusMeters} 米）</span></div>
          <div class="leave-reason"><b>时间：</b>${UI.fmtTzFull(a.startTime, a.timezone)}
            ～ ${UI.fmtTzFull(a.endTime, a.timezone)} <span style="color:var(--ink-muted)">（${UI.esc(a.timezone)}）</span></div>
          <div class="leave-reason" style="color:var(--ink-secondary)">${UI.esc(a.detail)}</div>
        </div>
        <div class="leave-actions">
          <button class="btn sm" data-roster="${a.id}">报名与打卡花名册</button>
          ${a.status === 'PUBLISHED' ? `<button class="btn sm danger" data-cancel="${a.id}">取消活动</button>` : ''}
        </div>
      </div>`;
    }

    async function publishModal(offices) {
      const officeOpts = isSuper
        ? offices.map((o) => `<option value="${o.officeId}">${UI.esc(o.officeName)}</option>`).join('')
        : '';
      const root2 = document.getElementById('modal-root');
      const mask = document.createElement('div');
      mask.className = 'modal-mask';
      mask.innerHTML = `
        <div class="modal" style="max-width:560px" role="dialog">
          <div class="modal-head">＋ 发布公益活动</div>
          <div class="modal-body">
            ${isSuper ? `<div class="field"><label>归属司法所</label><select class="input" id="f-office">${officeOpts}</select></div>` : ''}
            <div class="field"><label>活动标题</label><input class="input" id="f-title" maxlength="128" placeholder="如：社区环境清洁公益劳动"></div>
            <div class="field"><label>活动内容/要求</label><textarea class="input" id="f-detail" maxlength="1024" placeholder="服务内容、集合方式、注意事项（4 字以上）"></textarea></div>
            <div class="field"><label>活动地点名称</label><input class="input" id="f-addr" maxlength="256" placeholder="如：青山乡文化广场"></div>
            <div class="filter-bar">
              <div class="field" style="flex:1;margin-bottom:0"><label>纬度 lat</label><input class="input" id="f-lat" inputmode="decimal" placeholder="30.3581"></div>
              <div class="field" style="flex:1;margin-bottom:0"><label>经度 lng</label><input class="input" id="f-lng" inputmode="decimal" placeholder="114.4729"></div>
              <div class="field" style="width:120px;margin-bottom:0"><label>核验半径(米)</label><input class="input" id="f-radius" type="number" value="200" min="20" max="5000"></div>
            </div>
            <div class="filter-bar" style="margin-top:12px">
              <div class="field" style="flex:1;margin-bottom:0"><label>开始时间（当地）</label><input class="input" id="f-start" type="datetime-local"></div>
              <div class="field" style="flex:1;margin-bottom:0"><label>结束时间（当地）</label><input class="input" id="f-end" type="datetime-local"></div>
              <div class="field" style="width:110px;margin-bottom:0"><label>名额(可空)</label><input class="input" id="f-cap" type="number" min="1" max="9999" placeholder="不限"></div>
            </div>
            <div class="login-error" id="f-err" style="display:none;margin-top:10px"></div>
          </div>
          <div class="modal-foot">
            <button class="btn" id="f-cancel">取消</button>
            <button class="btn primary" id="f-ok">发布</button>
          </div>
        </div>`;
      root2.appendChild(mask);
      const close = () => mask.remove();
      mask.querySelector('#f-cancel').onclick = close;
      mask.addEventListener('click', (e) => { if (e.target === mask) close(); });
      mask.querySelector('#f-ok').onclick = async () => {
        const err = mask.querySelector('#f-err');
        const showErr = (m) => { err.style.display = 'block'; err.textContent = m; };
        const num = (v) => v === '' ? null : Number(v);
        const startStr = mask.querySelector('#f-start').value;
        const endStr = mask.querySelector('#f-end').value;
        if (!startStr || !endStr) return showErr('请选择活动开始与结束时间');
        const body = {
          officeId: isSuper ? Number(mask.querySelector('#f-office').value) : undefined,
          title: mask.querySelector('#f-title').value.trim(),
          detail: mask.querySelector('#f-detail').value.trim(),
          address: mask.querySelector('#f-addr').value.trim(),
          lat: num(mask.querySelector('#f-lat').value),
          lng: num(mask.querySelector('#f-lng').value),
          radiusMeters: num(mask.querySelector('#f-radius').value),
          startTime: new Date(startStr).toISOString(),
          endTime: new Date(endStr).toISOString(),
          capacity: num(mask.querySelector('#f-cap').value),
        };
        try {
          await Api.post('/activities', body);
          close();
          UI.toast('公益活动已发布，对象可在手机端报名', 'success');
          renderActivities();
        } catch (e) {
          showErr(e.message);
        }
      };
    }

    async function rosterModal(activityId) {
      let roster;
      try {
        roster = await Api.get(`/activities/${activityId}/roster`);
      } catch (e) { UI.toast(e.message, 'error'); return; }
      const abnormal = roster.filter((r) => r.result === 'ABNORMAL');
      await UI.alertModal('报名与打卡花名册', `
        <div style="font-size:13px;line-height:1.6">
          <div style="margin-bottom:8px">应到 ${roster.length} 人；
            <b style="color:var(--good)">正常打卡 ${roster.filter((r) => r.result === 'NORMAL').length}</b> ·
            <b style="color:${abnormal.length ? 'var(--critical)' : 'var(--ink-muted)'}">异常打卡 ${abnormal.length}</b> ·
            未打卡 ${roster.filter((r) => !r.result).length}</div>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>编号</th><th>脱敏名</th><th>报名</th><th>打卡</th><th>偏离距离</th></tr></thead>
              <tbody>
                ${roster.map((r) => {
                  const result = r.result === 'NORMAL'
                    ? '<span class="badge green">范围内·正常</span>'
                    : r.result === 'ABNORMAL'
                      ? '<span class="badge red">范围外·异常</span>'
                      : '<span class="badge gray">未打卡</span>';
                  const enroll = r.enrollmentStatus === 'ENROLLED'
                    ? '<span class="badge green">已报名</span>'
                    : '<span class="badge gray">已取消</span>';
                  const dist = r.result ? `${Math.round(r.distanceMeters)} / ${r.radiusMeters} 米` : '—';
                  return `<tr style="${r.result === 'ABNORMAL' ? 'background:var(--critical-bg)' : ''}">
                    <td>${UI.esc(r.correctionNo)}</td><td><b>${UI.esc(r.maskedName)}</b></td>
                    <td>${enroll}</td><td>${result}</td><td style="font-variant-numeric:tabular-nums">${dist}</td></tr>`;
                }).join('')}
              </tbody>
            </table>
          </div>
          ${abnormal.length ? `<div style="margin-top:8px;color:var(--critical)">异常打卡已生成违规红点，可在对象档案核查定位。</div>` : ''}
        </div>`, '📋');
    }

    async function cancelActivity(id) {
      try {
        await UI.confirmModal({
          title: '取消公益活动', icon: '🚫', danger: true,
          warn: '取消后对象不能再报名/打卡；已有报名与打卡记录保留留痕，不会删除。',
          bodyHtml: '确认取消该场公益活动？', confirmText: '确认取消',
        });
      } catch { return; }
      try {
        await Api.post(`/activities/${id}/cancel`, {});
        UI.toast('活动已取消', 'success');
        renderActivities();
      } catch (e) { UI.toast(e.message, 'error'); }
    }

    // ============================ 月度报到批量登记 ============================

    let candidates = [];

    async function renderMonthly() {
      const body = root.querySelector('#welfare-body');
      const now = new Date();
      const month = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
      body.innerHTML = `
        <div class="filter-bar">
          <label style="font-size:13px;color:var(--ink-secondary)">报到月份</label>
          <input class="input" type="month" id="m-month" value="${month}" style="width:auto">
          <button class="btn sm" id="m-load">载入对象</button>
          <span style="flex:1"></span>
          <button class="btn sm" id="m-all">全选可登记</button>
          <button class="btn sm primary" id="m-submit">批量完成报到（0）</button>
        </div>
        <div id="m-list"><div class="skeleton">请选择月份后载入对象</div></div>
        <div id="m-result"></div>`;

      root.querySelector('#m-load').onclick = loadCandidates;
      root.querySelector('#m-all').onclick = () => {
        root.querySelectorAll('input[data-cid]').forEach((cb) => {
          if (!cb.disabled) cb.checked = true;
        });
        updateCount();
      };
      root.querySelector('#m-submit').onclick = submitBatch;
      loadCandidates();
    }

    async function loadCandidates() {
      const month = root.querySelector('#m-month').value;
      const listEl = root.querySelector('#m-list');
      const resEl = root.querySelector('#m-result');
      resEl.innerHTML = '';
      listEl.innerHTML = '<div class="skeleton">对象载入中…</div>';
      try {
        candidates = await Api.get('/monthly-reports/candidates?month=' + encodeURIComponent(month));
      } catch (e) {
        listEl.innerHTML = ErrorState('对象载入失败', e.message);
        return;
      }
      const showOffice = isSuper;
      listEl.innerHTML = `
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th style="width:36px"></th><th>编号</th><th>脱敏名</th>
              ${showOffice ? '<th>司法所</th>' : ''}<th>状态</th><th>本月当面报到</th></tr></thead>
            <tbody>
              ${candidates.map((c) => `
                <tr style="${!c.eligible ? 'opacity:.55' : ''}">
                  <td><input type="checkbox" data-cid="${c.objectId}" ${!c.eligible || c.reported ? 'disabled' : ''}
                    ${c.reported ? '' : ''}></td>
                  <td>${UI.esc(c.correctionNo)}</td>
                  <td><b>${UI.esc(c.maskedName)}</b></td>
                  ${showOffice ? `<td>${UI.esc(c.officeName)}</td>` : ''}
                  <td>${UI.statusBadge(c.status)}</td>
                  <td>${c.reported
                    ? '<span class="badge green">✅ 已登记</span>'
                    : c.eligible ? '<span class="badge gray">未登记</span>'
                      : '<span class="badge gray">不可登记</span>'}</td>
                </tr>`).join('')}
            </tbody>
          </table>
        </div>`;
      listEl.querySelectorAll('input[data-cid]').forEach((cb) => cb.onchange = updateCount);
      updateCount();
    }

    function selectedIds() {
      return Array.from(root.querySelectorAll('input[data-cid]:checked'))
        .map((cb) => Number(cb.dataset.cid));
    }
    function updateCount() {
      root.querySelector('#m-submit').textContent = '批量完成报到（' + selectedIds().length + '）';
    }

    async function submitBatch() {
      const ids = selectedIds();
      if (!ids.length) { UI.toast('请先勾选要登记的对象', 'warn'); return; }
      const month = root.querySelector('#m-month').value;
      const btn = root.querySelector('#m-submit');
      btn.disabled = true;
      try {
        await UI.confirmModal({
          title: `批量登记 ${ids.length} 名对象月度报到`, icon: '📋',
          warn: '系统将逐个独立登记，单个人失败（如当月已登记/状态不符）不影响其他人，结果逐条列出。',
          bodyHtml: `月份 <b>${UI.esc(month)}</b>，确认提交 ${ids.length} 人的当面月度报到？`,
          confirmText: '确认批量登记',
        });
      } catch { btn.disabled = false; return; }
      try {
        const res = await Api.post('/monthly-reports/batch', { objectIds: ids, month });
        renderBatchResult(res);
        await loadCandidates();
      } catch (e) {
        UI.toast(e.message, 'error');
      } finally {
        btn.disabled = false;
      }
    }

    function renderBatchResult(res) {
      const resEl = root.querySelector('#m-result');
      const ok = res.items.filter((i) => i.success);
      const bad = res.items.filter((i) => !i.success);
      resEl.innerHTML = `
        <div class="card" style="margin-top:14px">
          <div class="card-title">🧾 批量登记结果
            <span class="sub">共 ${res.total} 人 ·
              <b style="color:var(--good)">成功 ${res.succeeded}</b> ·
              <b style="color:${res.failed ? 'var(--critical)' : 'var(--ink-muted)'}">失败 ${res.failed}</b></span>
          </div>
          ${ok.length ? `<div style="font-size:13px;margin-bottom:6px;color:var(--good)">✅ 已登记：</div>
            <div class="batch-ok">${ok.map((i) => `<span class="batch-chip ok">${UI.esc(i.maskedName)} ${UI.esc(i.correctionNo)}</span>`).join('')}</div>` : ''}
          ${bad.length ? `<div style="font-size:13px;margin:10px 0 6px;color:var(--critical)">⚠️ 未登记（逐条原因）：</div>
            <div class="table-wrap"><table class="data">
              <thead><tr><th>编号</th><th>脱敏名</th><th>卡在哪</th></tr></thead>
              <tbody>${bad.map((i) => `<tr>
                <td>${UI.esc(i.correctionNo)}</td><td><b>${UI.esc(i.maskedName)}</b></td>
                <td style="color:var(--critical)">${UI.esc(i.reason)}</td></tr>`).join('')}
              </tbody></table></div>`
            : '<div style="color:var(--good);font-size:13px;margin-top:8px">全部登记成功。</div>'}
        </div>`;
      resEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    await renderActivities();
  };
})(window);
