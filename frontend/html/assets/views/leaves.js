/* 请销假两级审批（司法所初审 → 区局复核，退回可重提并留痕） */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const STATUS_STYLE = {
    PENDING_OFFICE: ['gray', '待司法所初审'],
    PENDING_BUREAU: ['gray', '待区局复核'],
    RETURNED: ['warn-badge', '已退回·待重提'],
    APPROVED: ['green', '已批准·假期中'],
    OVERDUE: ['red', '逾假未归'],
    COMPLETED: ['green', '已销假'],
    CANCELLED: ['gray', '已撤回'],
  };
  const TYPE_LABEL = { PERSONAL: '事假', SICK: '病假', OTHER: '其他' };

  Views.leaves = async function (root) {
    const session = Api.getSession();
    const isSuper = session.role === 'SUPERVISOR';
    let tab = isSuper ? 'BUREAU' : 'OFFICE';

    root.innerHTML = `
      <div class="page-head">
        <h2>📝 请销假审批</h2>
        <div class="desc">两级审批：司法所初审通过后报区司法局复核；任一环可退回，对象修改后可重提，全程留痕。
          准假期间越出活动围栏不计越界，假期结束未销假系统自动升违规。</div>
      </div>
      <div class="card">
        <div class="filter-bar" id="leave-tabs"></div>
        <div id="leave-list"><div class="skeleton">审批单加载中…</div></div>
      </div>`;

    const tabs = [];
    if (!isSuper) tabs.push(['OFFICE', '待我初审']);
    if (isSuper) tabs.push(['BUREAU', '待我区局复核']);
    tabs.push(['ACTIVE', '审批中/假期中']);
    const tabBar = root.querySelector('#leave-tabs');
    tabBar.innerHTML = tabs.map(([k, label]) =>
      `<button class="btn sm ${k === tab ? 'primary' : ''}" data-tab="${k}">${label}</button>`).join('');

    tabBar.querySelectorAll('[data-tab]').forEach((b) => {
      b.onclick = () => {
        tab = b.dataset.tab;
        tabBar.querySelectorAll('[data-tab]').forEach((x) =>
          x.classList.toggle('primary', x.dataset.tab === tab));
        load();
      };
    });

    async function load() {
      const listEl = root.querySelector('#leave-list');
      listEl.innerHTML = '<div class="skeleton">加载中…</div>';
      try {
        const q = tab === 'ACTIVE' ? '' : '?queue=' + tab;
        const items = await Api.get('/leaves' + q);
        render(items);
      } catch (e) {
        if (e.code === 'FORBIDDEN') {
          listEl.innerHTML = `<div class="state-box forbidden"><div class="ico">🚫</div><h3>无权访问该审批环节</h3>
            <p>${UI.esc(e.message)}</p></div>`;
        } else {
          listEl.innerHTML = ErrorState('审批单加载失败', e.message);
        }
      }
    }

    function render(items) {
      const listEl = root.querySelector('#leave-list');
      if (!items.length) {
        listEl.innerHTML = `<div class="state-box"><div class="ico">🗂️</div><h3>当前环节没有待办</h3>
          <p>${tab === 'ACTIVE' ? '暂无审批中或假期中的请假单。' : '该审批环节暂无待处理单据。'}</p></div>`;
        return;
      }
      listEl.innerHTML = items.map(leaveCard).join('');
      bindCardEvents();
    }

    function leaveCard(l) {
      const [badgeCls, badgeText] = STATUS_STYLE[l.status] || ['gray', l.statusLabel];
      const tz = l.timezone;
      const canOffice = l.status === 'PENDING_OFFICE';
      const canBureau = l.status === 'PENDING_BUREAU' && isSuper;
      const canReturn = l.status === 'APPROVED' || l.status === 'OVERDUE';
      return `
      <div class="leave-card" data-id="${l.id}">
        <div class="leave-head">
          <b class="leave-title">${UI.esc(l.maskedName)} · ${UI.esc(l.correctionNo)}</b>
          <span class="badge ${badgeCls === 'warn-badge' ? 'gray' : badgeCls}">${UI.esc(badgeText)}</span>
          <span style="flex:1"></span>
          <span class="leave-meta">${UI.esc(l.officeName)} · ${UI.esc(TYPE_LABEL[l.leaveType] || l.leaveType)}</span>
        </div>
        <div class="leave-body">
          <div class="leave-reason"><b>请假事由：</b>${UI.esc(l.reason)}</div>
          <div class="leave-reason"><b>目的地：</b>${UI.esc(l.destination)}</div>
          <div class="leave-reason"><b>假期：</b>${UI.fmtTzFull(l.startTime, tz)} ～ ${UI.fmtTzFull(l.endTime, tz)}
            <span style="color:var(--ink-muted)">（${UI.esc(tz)}）</span></div>
          <details class="leave-trail">
            <summary>审批/销假留痕（${l.events.length} 步）</summary>
            <div class="timeline" style="margin-top:8px">
              ${l.events.map((ev) => `
                <div class="tl-item">
                  <div class="tl-line"><b>${UI.esc(ev.actionLabel)}</b>
                    <span class="badge gray" style="margin-left:6px">${UI.esc(ev.resultStatusLabel)}</span></div>
                  <div class="tl-meta">${UI.esc(ev.operatorName)} · ${UI.fmtTzFull(ev.occurredAt, tz)}
                    ${ev.comment ? '· ' + UI.esc(ev.comment) : ''}</div>
                </div>`).join('')}
            </div>
          </details>
        </div>
        <div class="leave-actions">
          ${canOffice ? `
            <button class="btn sm primary" data-act="office-approve">初审通过·报区局复核</button>
            <button class="btn sm danger" data-act="office-return">初审退回</button>` : ''}
          ${canBureau ? `
            <button class="btn sm primary" data-act="bureau-approve">复核通过·准予外出</button>
            <button class="btn sm danger" data-act="bureau-return">复核退回</button>` : ''}
          ${canReturn ? `
            <button class="btn sm ${l.status === 'OVERDUE' ? 'danger' : 'primary'}" data-act="staff-return">
              ${l.status === 'OVERDUE' ? '逾假后销假·恢复在矫' : '代登记当面销假'}</button>` : ''}
          ${l.status === 'RETURNED' ? '<span class="leave-meta">已退回对象，等待修改重提</span>' : ''}
        </div>
      </div>`;
    }

    function bindCardEvents() {
      root.querySelectorAll('.leave-card').forEach((card) => {
        const id = card.dataset.id;
        card.querySelectorAll('[data-act]').forEach((btn) => {
          btn.onclick = () => decide(id, btn.dataset.act);
        });
      });
    }

    async function decide(id, act) {
      const isReturn = act.endsWith('return') && act !== 'staff-return';
      const isStaffReturn = act === 'staff-return';
      let path, body, title;
      if (act === 'office-approve') {
        path = `/leaves/${id}/office-decision`; title = '司法所初审通过';
      } else if (act === 'office-return') {
        path = `/leaves/${id}/office-decision`; title = '司法所初审退回';
      } else if (act === 'bureau-approve') {
        path = `/leaves/${id}/bureau-decision`; title = '区局复核通过（终批准假）';
      } else if (act === 'bureau-return') {
        path = `/leaves/${id}/bureau-decision`; title = '区局复核退回';
      } else {
        path = `/leaves/${id}/staff-return`; title = '代登记当面销假';
      }

      try {
        if (isStaffReturn) {
          await UI.confirmModal({
            title, icon: '🔄',
            warn: '销假后对象按状态机恢复「在矫」；若已逾假，违规红点保留，仅恢复监管状态。',
            bodyHtml: '确认该对象已返所并办理当面销假？',
            confirmText: '确认销假',
          });
          await Api.post(path, { note: '' });
        } else if (isReturn) {
          const reason = await UI.confirmModal({
            title, icon: '↩️', danger: true,
            warn: '退回后对象可在手机端修改请假内容并重新提交，退回意见将全程留痕。',
            bodyHtml: '请填写退回意见（不少于 4 字），对象将据此修改：',
            requireReason: true, reasonLabel: '退回意见',
            reasonPlaceholder: '如：就医证明材料不全，请上传诊断证明后重提',
            confirmText: '确认退回',
          });
          await Api.post(path, { approve: false, comment: reason });
        } else {
          await UI.confirmModal({
            title, icon: '✅',
            warn: act === 'bureau-approve'
              ? '终批后对象状态置「请假外出」，假期内越出活动围栏不计越界；假期结束未销假将自动升违规。'
              : '初审通过后单据流转至区司法局等待复核。',
            bodyHtml: '确认通过该请假申请？',
            confirmText: '确认通过',
          });
          await Api.post(path, { approve: true, comment: '' });
        }
        UI.toast('操作已提交并留痕', 'success');
        load();
      } catch (e) {
        if (e && e.message === 'cancel') return;
        if (e.code === 'LEAVE_WRONG_STAGE' || e.code === 'RETURN_REASON_REQUIRED'
            || e.code === 'FORBIDDEN' || e.code === 'INVALID_TRANSITION'
            || e.code === 'CONCURRENT_MODIFICATION') {
          UI.alertModal('操作未完成',
            `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
          load();
        } else if (e.message) {
          UI.toast(e.message, 'error');
        }
      }
    }

    await load();
  };
})(window);
