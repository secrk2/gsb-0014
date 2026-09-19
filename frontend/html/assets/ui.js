/* 通用 UI：toast、modal、转义、状态文案 */
(function (global) {
  const STATUS_LABEL = {
    INTAKE: '入矫登记', SERVING: '在矫', LEAVE: '请假外出',
    ADMONISHED: '训诫', REIMPRISONED: '收监', RELEASED: '解除',
  };
  const STATUS_ICON = {
    INTAKE: '📝', SERVING: '✅', LEAVE: '🚪',
    ADMONISHED: '⚠️', REIMPRISONED: '🔒', RELEASED: '📭',
  };
  const WEEK_LABEL = {
    MONDAY: '周一', TUESDAY: '周二', WEDNESDAY: '周三', THURSDAY: '周四',
    FRIDAY: '周五', SATURDAY: '周六', SUNDAY: '周日',
  };

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function statusBadge(status) {
    return `<span class="badge ${esc(status)}"><span class="b-ico">${STATUS_ICON[status] || ''}</span>${esc(STATUS_LABEL[status] || status)}</span>`;
  }

  function fmtDateTime(s) {
    if (!s) return '—';
    return String(s).replace('T', ' ').slice(0, 16);
  }

  // ---------- UTC 时刻 → 指定 IANA 时区展示 ----------
  // 后端时间一律 UTC（ISO 带 Z）；“今天/星期/时段”全部按对象所属司法所时区换算，
  // 不能用干警浏览器时区，否则跨时区对象会把越界算错。
  function tzParts(s, tz) {
    const d = new Date(s);
    if (isNaN(d.getTime())) return null;
    const dtf = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz || 'Asia/Shanghai', hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', weekday: 'short',
    });
    const m = {};
    dtf.formatToParts(d).forEach((p) => { m[p.type] = p.value; });
    if (m.hour === '24') m.hour = '00';
    return m;
  }

  /** UTC ISO → "MM-dd HH:mm:ss"（按 tz） */
  function fmtTz(s, tz) {
    const m = tzParts(s, tz);
    return m ? `${m.month}-${m.day} ${m.hour}:${m.minute}:${m.second}` : '—';
  }
  /** UTC ISO → "yyyy-MM-dd HH:mm"（按 tz） */
  function fmtTzFull(s, tz) {
    const m = tzParts(s, tz);
    return m ? `${m.year}-${m.month}-${m.day} ${m.hour}:${m.minute}` : '—';
  }
  function fmtTzClock(s, tz) {
    const m = tzParts(s, tz);
    return m ? `${m.hour}:${m.minute}:${m.second}` : '—';
  }
  function tzDate(s, tz) {
    const m = tzParts(s, tz);
    return m ? `${m.year}-${m.month}-${m.day}` : '';
  }
  /** 心跳年龄（秒）→ 中文年龄文案 */
  function fmtAge(sec) {
    if (sec == null) return '从无回传';
    if (sec < 60) return sec + ' 秒前';
    if (sec < 3600) return Math.floor(sec / 60) + ' 分钟前';
    return Math.floor(sec / 3600) + ' 小时前';
  }

  let toastTimer = new Map();
  function toast(message, type) {
    type = type || 'info';
    const root = document.getElementById('toast-root');
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    root.appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transition = 'opacity .25s';
      setTimeout(() => el.remove(), 260);
    }, 3800);
  }

  /**
   * 确认弹窗。options: {title, bodyHtml, confirmText, danger, requireReason}
   * 返回 Promise：确认时 resolve(reason|null)，取消时 reject('cancel')
   */
  function confirmModal(options) {
    return new Promise((resolve, reject) => {
      const root = document.getElementById('modal-root');
      const mask = document.createElement('div');
      mask.className = 'modal-mask';
      const reasonField = options.requireReason ? `
        <div class="field" style="margin-top:14px">
          <label>请填写${esc(options.reasonLabel || '理由')}（必填，将留痕）</label>
          <textarea class="input" id="modal-reason" minlength="4" maxlength="256"
            placeholder="例如：${esc(options.reasonPlaceholder || '司法所工作核查需要')}"></textarea>
        </div>` : '';
      mask.innerHTML = `
        <div class="modal ${options.requireReason ? 'warn' : ''}" role="dialog" aria-modal="true">
          <div class="modal-head">${options.icon ? options.icon + ' ' : ''}${esc(options.title || '请确认')}</div>
          <div class="modal-body">
            ${options.warn ? `<div class="confirm-warn">⚠️ ${esc(options.warn)}</div>` : ''}
            <div>${options.bodyHtml || ''}</div>
            ${reasonField}
            <div class="modal-error" id="modal-err" style="display:none"></div>
          </div>
          <div class="modal-foot">
            <button class="btn" id="modal-cancel">取消</button>
            <button class="btn ${options.danger ? 'danger' : 'primary'}" id="modal-ok">${esc(options.confirmText || '确认')}</button>
          </div>
        </div>`;
      root.appendChild(mask);
      const reasonEl = mask.querySelector('#modal-reason');
      if (reasonEl) setTimeout(() => reasonEl.focus(), 30);

      function close() { mask.remove(); }
      mask.querySelector('#modal-cancel').onclick = () => { close(); reject(new Error('cancel')); };
      mask.addEventListener('click', (e) => { if (e.target === mask) { close(); reject(new Error('cancel')); } });
      mask.querySelector('#modal-ok').onclick = () => {
        const reason = reasonEl ? reasonEl.value.trim() : null;
        if (options.requireReason && reason.length < 4) {
          const err = mask.querySelector('#modal-err');
          err.style.display = 'block';
          err.className = 'login-error';
          err.textContent = '理由不少于 4 个字，该操作将被审计留痕';
          return;
        }
        close();
        resolve(reason);
      };
    });
  }

  /** 普通信息弹窗 */
  function alertModal(title, bodyHtml, icon) {
    return new Promise((resolve) => {
      const root = document.getElementById('modal-root');
      const mask = document.createElement('div');
      mask.className = 'modal-mask';
      mask.innerHTML = `
        <div class="modal" role="dialog">
          <div class="modal-head">${icon ? icon + ' ' : ''}${esc(title)}</div>
          <div class="modal-body">${bodyHtml}</div>
          <div class="modal-foot"><button class="btn primary" id="modal-ok">我知道了</button></div>
        </div>`;
      root.appendChild(mask);
      mask.querySelector('#modal-ok').onclick = () => { mask.remove(); resolve(); };
      mask.addEventListener('click', (e) => { if (e.target === mask) { mask.remove(); resolve(); } });
    });
  }

  global.UI = {
    esc, statusBadge, fmtDateTime, toast, confirmModal, alertModal,
    fmtTz, fmtTzFull, fmtTzClock, tzDate, fmtAge, tzParts,
    statusIcon: (s) => STATUS_ICON[s] || '',
    STATUS_LABEL, STATUS_ICON, WEEK_LABEL,
  };
})(window);
