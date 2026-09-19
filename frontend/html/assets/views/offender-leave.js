/* 对象手机端：请假与销假面板（两级审批进度、退回重提、销假） */
(function (global) {
  const TYPE_OPTIONS = [['PERSONAL', '事假'], ['SICK', '病假'], ['OTHER', '其他']];
  const STATUS_BADGE = {
    PENDING_OFFICE: ['gray', '待司法所初审'],
    PENDING_BUREAU: ['gray', '待区局复核'],
    RETURNED: ['red', '已退回·请修改重提'],
    APPROVED: ['green', '已批准·假期中'],
    OVERDUE: ['red', '逾假未归'],
    COMPLETED: ['green', '已销假'],
    CANCELLED: ['gray', '已撤回'],
  };
  const STAGE_LABEL = {
    SUBMITTED: '提交申请', OFFICE_APPROVED: '司法所初审',
    BUREAU_APPROVED: '区局复核', RETURN_CHECKIN: '销假返所',
  };

  async function mount(root, o) {
    await render(root, o);
  }

  async function render(root, o) {
    const body = root.querySelector('#leave-body');
    let leaves = [];
    try {
      leaves = await Api.get('/offender/leaves');
    } catch (e) {
      body.innerHTML = `<div style="color:var(--critical);font-size:12.5px">加载失败：${UI.esc(e.message)}</div>`;
      return;
    }
    const active = leaves.find((l) => ['PENDING_OFFICE', 'PENDING_BUREAU', 'RETURNED', 'APPROVED', 'OVERDUE'].includes(l.status));

    let html = '';
    if (active) html += activeCard(active, o);
    else html += `<div style="font-size:12.5px;color:var(--ink-muted);margin-bottom:10px">
        当前没有在审批或假期中的请假单。请假须司法所初审、区局复核两级通过后方可外出。</div>`;

    if (!active || ['COMPLETED', 'CANCELLED'].includes(active.status)) {
      html += `<button class="btn sm primary" id="lv-new" style="width:100%">＋ 发起请假申请</button>`;
    }

    if (active) {
      html += `<details style="margin-top:10px"><summary style="font-size:12.5px;color:var(--brand-500)">
        查看审批留痕（${active.events.length} 步）</summary>
        <div class="timeline" style="margin-top:8px">
          ${active.events.map((ev) => `
            <div class="tl-item">
              <div class="tl-line" style="font-size:12.5px"><b>${UI.esc(ev.actionLabel)}</b></div>
              <div class="tl-meta">${UI.fmtTzFull(ev.occurredAt, o.timezone)}
                ${ev.comment ? '· ' + UI.esc(ev.comment) : ''}</div>
            </div>`).join('')}
        </div></details>`;
    }
    body.innerHTML = html;

    const newBtn = root.querySelector('#lv-new');
    if (newBtn) newBtn.onclick = () => applyModal(root, o, null);
    if (active) bindActive(root, o, active);
  }

  function activeCard(l, o) {
    const [cls, text] = STATUS_BADGE[l.status];
    const overdueNote = l.status === 'OVERDUE'
      ? `<div class="confirm-warn" style="margin:8px 0">⛔ 假期已于 ${UI.fmtTzFull(l.endTime, o.timezone)} 截止，
          系统已按“逾假未归”登记违规。请立即返所销假，销假后恢复在矫（违规记录保留）。</div>` : '';
    const leaveNote = l.status === 'APPROVED'
      ? `<div style="font-size:12px;color:var(--good);margin:6px 0">✅ 准假外出期间，定位越过司法所活动围栏不计越界；
          但法定禁区仍不得进入。请在截止时间前销假。</div>` : '';

    let actions = '';
    if (l.status === 'RETURNED') {
      const lastReturn = [...l.events].reverse().find((e) => e.action.endsWith('RETURNED'));
      actions = `<div class="confirm-warn" style="margin:6px 0">↩️ 退回意见：${UI.esc(lastReturn ? lastReturn.comment : '')}</div>
        <div style="display:flex;gap:8px">
          <button class="btn sm primary" data-a="resubmit" style="flex:1">修改后重新提交</button>
          <button class="btn sm" data-a="withdraw">撤回</button></div>`;
    } else if (l.status === 'PENDING_OFFICE' || l.status === 'PENDING_BUREAU') {
      actions = `<button class="btn sm" data-a="withdraw" style="width:100%">撤回申请</button>`;
    } else if (l.status === 'APPROVED' || l.status === 'OVERDUE') {
      actions = `<button class="big-check-btn" data-a="return" style="padding:13px;font-size:15px">
        ${l.status === 'OVERDUE' ? '⛔ 立即销假返所' : '我已返所·销假'}</button>`;
    }

    return `
      <div style="border:1px solid var(--hairline);border-radius:10px;padding:12px;margin-bottom:10px">
        <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
          <span class="badge ${cls}">${text}</span>
          <span style="font-size:12.5px;color:var(--ink-muted)">${UI.esc(l.leaveTypeLabel)} · 单号 #${l.id}</span>
        </div>
        <dl class="kv" style="grid-template-columns:64px 1fr;margin-top:8px">
          <dt>目的地</dt><dd>${UI.esc(l.destination)}</dd>
          <dt>假期</dt><dd>${UI.fmtTzFull(l.startTime, o.timezone)} ～ ${UI.fmtTzFull(l.endTime, o.timezone)}</dd>
          <dt>事由</dt><dd>${UI.esc(l.reason)}</dd>
        </dl>
        ${leaveNote}${overdueNote}${actions}
      </div>`;
  }

  function bindActive(root, o, l) {
    const btn = root.querySelector('[data-a]');
    if (!btn) return;
    root.querySelectorAll('[data-a]').forEach((b) => {
      b.onclick = () => act(root, o, l, b.dataset.a);
    });
  }

  async function act(root, o, l, a) {
    try {
      if (a === 'withdraw') {
        await UI.confirmModal({ title: '撤回请假申请', bodyHtml: '确认撤回当前请假单？撤回后可重新发起。', confirmText: '确认撤回' });
        await Api.post(`/offender/leaves/${l.id}/withdraw`);
        UI.toast('已撤回', 'success');
      } else if (a === 'return') {
        await UI.confirmModal({
          title: l.status === 'OVERDUE' ? '逾假销假' : '销假返所',
          warn: l.status === 'OVERDUE' ? '您已逾假未归，本次销假后恢复在矫，逾假违规记录将保留。' : '销假后恢复「在矫」，活动围栏越界判定立即恢复。',
          bodyHtml: '确认本人已返所，办理销假？', confirmText: '确认销假',
          danger: l.status === 'OVERDUE',
        });
        await Api.post(`/offender/leaves/${l.id}/return`, { note: '' });
        UI.toast('销假成功，已恢复在矫', 'success');
      } else if (a === 'resubmit') {
        return applyModal(root, o, l);
      }
      render(root, o);
    } catch (e) {
      if (e && e.message === 'cancel') return;
      UI.toast(e.message, 'error');
    }
  }

  function applyModal(root, o, existing) {
    const modalRoot = document.getElementById('modal-root');
    const mask = document.createElement('div');
    mask.className = 'modal-mask';
    const isResubmit = !!existing;
    const startDefault = pad(new Date(Date.now() + 3600 * 1000));
    const endDefault = pad(new Date(Date.now() + 25 * 3600 * 1000));
    mask.innerHTML = `
      <div class="modal" style="max-width:460px" role="dialog">
        <div class="modal-head">${isResubmit ? '修改请假单并重新提交' : '发起请假申请'}</div>
        <div class="modal-body">
          <div class="field"><label>请假类型</label>
            <select class="input" id="q-type">
              ${TYPE_OPTIONS.map(([v, t]) => `<option value="${v}" ${existing && existing.leaveType === v ? 'selected' : ''}>${t}</option>`).join('')}
            </select></div>
          <div class="field"><label>请假事由（≥4 字）</label>
            <textarea class="input" id="q-reason" maxlength="512" placeholder="如：陪同家属到县医院就诊">${existing ? UI.esc(existing.reason) : ''}</textarea></div>
          <div class="field"><label>外出目的地</label>
            <input class="input" id="q-dest" maxlength="256" value="${existing ? UI.esc(existing.destination) : ''}" placeholder="如：县人民医院"></div>
          <div class="filter-bar">
            <div class="field" style="flex:1;margin-bottom:0"><label>开始时间</label>
              <input class="input" type="datetime-local" id="q-start" value="${startDefault}"></div>
            <div class="field" style="flex:1;margin-bottom:0"><label>截止时间</label>
              <input class="input" type="datetime-local" id="q-end" value="${endDefault}"></div>
          </div>
          <div class="login-error" id="q-err" style="display:none;margin-top:10px"></div>
        </div>
        <div class="modal-foot">
          <button class="btn" id="q-cancel">取消</button>
          <button class="btn primary" id="q-ok">${isResubmit ? '重新提交' : '提交申请'}</button>
        </div>
      </div>`;
    modalRoot.appendChild(mask);
    const close = () => mask.remove();
    mask.querySelector('#q-cancel').onclick = close;
    mask.addEventListener('click', (e) => { if (e.target === mask) close(); });
    mask.querySelector('#q-ok').onclick = async () => {
      const err = mask.querySelector('#q-err');
      const body = {
        leaveType: mask.querySelector('#q-type').value,
        reason: mask.querySelector('#q-reason').value.trim(),
        destination: mask.querySelector('#q-dest').value.trim(),
        startTime: new Date(mask.querySelector('#q-start').value).toISOString(),
        endTime: new Date(mask.querySelector('#q-end').value).toISOString(),
      };
      try {
        if (isResubmit) await Api.put(`/offender/leaves/${existing.id}/resubmit`, body);
        else await Api.post('/offender/leaves', body);
        close();
        UI.toast(isResubmit ? '已重新提交，重新走两级审批' : '申请已提交，等待司法所初审', 'success');
        render(root, o);
      } catch (e) {
        err.style.display = 'block';
        err.textContent = e.message;
      }
    };
  }

  function pad(d) {
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  global.LeavePanel = { mount };
})(window);
