/* 对象手机端：公益活动报名 + 现场打卡（服务端核验是否落在活动点范围内） */
(function (global) {
  let fixByActivity = {}; // activityId -> {lat,lng,fixTs,mode}

  async function mount(root, o) {
    await render(root, o);
  }

  async function render(root, o) {
    const body = root.querySelector('#activity-body');
    let list = [];
    try {
      list = await Api.get('/offender/activities');
    } catch (e) {
      body.innerHTML = `<div style="color:var(--critical);font-size:12.5px">活动加载失败：${UI.esc(e.message)}</div>`;
      return;
    }
    if (!list.length) {
      body.innerHTML = `<div style="font-size:12.5px;color:var(--ink-muted)">暂无可报名的公益活动。
        活动由司法所发布后会显示在这里，可手机报名、现场打卡。</div>`;
      return;
    }

    body.innerHTML = list.map((a) => card(a, o)).join('');
    bindAll(root, o, list);
  }

  function card(a, o) {
    let stateLine = '';
    if (a.myEnrollmentStatus === 'ENROLLED') {
      stateLine = '<span class="badge green">已报名</span>';
    } else if (a.myEnrollmentStatus === 'CANCELLED') {
      stateLine = '<span class="badge gray">报名已取消</span>';
    }
    if (a.full && a.myEnrollmentStatus !== 'ENROLLED') stateLine += ' <span class="badge red">已满员</span>';
    if (a.myAttendanceResult === 'NORMAL') stateLine += ' <span class="badge green">✅ 已正常打卡</span>';
    if (a.myAttendanceResult === 'ABNORMAL') stateLine += ' <span class="badge red">⚠️ 打卡异常</span>';

    let actions = '';
    if (a.myAttendanceResult) {
      actions = `<div style="font-size:12.5px;color:${
        a.myAttendanceResult === 'NORMAL' ? 'var(--good)' : 'var(--critical)'}">
        ${a.myAttendanceResult === 'NORMAL'
          ? `打卡定位距活动点 ${Math.round(a.myDistanceMeters)} 米，在核验范围内。`
          : `打卡定位距活动点 ${Math.round(a.myDistanceMeters)} 米，超出核验范围，已标记异常并通知司法所。`}</div>`;
    } else if (a.canPunch) {
      actions = punchBlock(a);
    } else if (a.canEnroll) {
      actions = `<button class="btn sm primary" data-e="enroll" style="width:100%">我要报名</button>`;
    } else if (a.canCancel) {
      actions = `<div style="display:flex;gap:8px">
        <span style="flex:1;font-size:12px;color:var(--ink-secondary);align-self:center">已报名，待到场打卡</span>
        <button class="btn sm danger" data-e="cancel">取消报名</button></div>`;
    } else if (a.myEnrollmentStatus === 'ENROLLED') {
      actions = '<div style="font-size:12.5px;color:var(--ink-muted)">已报名，请在活动时间内到此页面现场打卡。</div>';
    }

    return `
      <div style="border:1px solid var(--hairline);border-radius:10px;padding:12px;margin-bottom:10px" data-aid="${a.id}">
        <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center">
          <b style="font-size:14px">${UI.esc(a.title)}</b>${stateLine}
        </div>
        <div style="font-size:12.5px;color:var(--ink-secondary);margin-top:5px">
          📍 ${UI.esc(a.address)}（核验半径 ${a.radiusMeters} 米）</div>
        <div style="font-size:12.5px;color:var(--ink-muted);margin-top:2px">
          🕒 ${UI.fmtTzFull(a.startTime, o.timezone)} ～ ${UI.fmtTzFull(a.endTime, o.timezone)}</div>
        <div style="font-size:12.5px;color:var(--ink-secondary);margin-top:5px">${UI.esc(a.detail)}</div>
        <div style="font-size:12px;color:var(--ink-muted);margin-top:4px">已报名 ${a.enrolledCount}${
          a.capacity ? '/' + a.capacity : ''} 人</div>
        <div style="margin-top:9px">${actions}</div>
      </div>`;
  }

  function punchBlock(a) {
    const fix = fixByActivity[a.id];
    const fixLine = fix
      ? `<div style="font-size:12px;margin-bottom:6px">
          当前定位（${(Date.now() - fix.fixTs > 300000)
            ? '<span style="color:var(--warning)">已过期，请重新获取</span>'
            : '<span style="color:var(--good)">有效</span>'}）：
          ${fix.lat.toFixed(5)},${fix.lng.toFixed(5)} · ${fix.mode === 'in' ? '活动点范围内' : '活动点范围外'}</div>`
      : '<div style="font-size:12px;color:var(--ink-muted);margin-bottom:6px">打卡必须使用活动现场的<b>当前</b>定位。</div>';
    return `
      <div class="confirm-warn" style="margin-bottom:8px">📍 现在处于活动打卡时段，到场后获取定位再打卡。</div>
      ${fixLine}
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px">
        <button class="btn sm" data-e="gps">🛰 真实 GPS</button>
        <button class="btn sm" data-e="fix-in">模拟（活动点内）</button>
        <button class="btn sm" data-e="fix-out">模拟（活动点外）</button>
      </div>
      <button class="big-check-btn" data-e="punch" style="padding:13px;font-size:15px">现场打卡</button>`;
  }

  function bindAll(root, o, list) {
    root.querySelectorAll('[data-aid]').forEach((el) => {
      const aid = Number(el.dataset.aid);
      const a = list.find((x) => x.id === aid);
      el.querySelectorAll('[data-e]').forEach((btn) => {
        btn.onclick = () => onEvent(root, o, a, btn.dataset.e);
      });
    });
  }

  async function onEvent(root, o, a, ev) {
    try {
      if (ev === 'enroll') {
        await Api.post(`/offender/activities/${a.id}/enroll`);
        UI.toast('报名成功，活动时间内可现场打卡', 'success');
      } else if (ev === 'cancel') {
        await Api.post(`/offender/activities/${a.id}/cancel`);
        UI.toast('已取消报名');
      } else if (ev === 'gps') {
        await acquireGps(a);
      } else if (ev === 'fix-in') {
        const inOff = Math.min(10, a.radiusMeters * 0.3) / 111000;
        fixByActivity[a.id] = { lat: a.lat + inOff, lng: a.lng, fixTs: Date.now(), mode: 'in' };
        UI.toast('已获取定位：当前在活动点核验范围内', 'success');
      } else if (ev === 'fix-out') {
        const outOff = (a.radiusMeters + 300) / 111000;
        fixByActivity[a.id] = { lat: a.lat + outOff, lng: a.lng + outOff * 0.5, fixTs: Date.now(), mode: 'out' };
        UI.toast('已获取定位：当前在活动点核验范围外', 'warn');
      } else if (ev === 'punch') {
        return doPunch(root, o, a);
      }
      render(root, o);
    } catch (e) {
      if (e.code === 'STALE_LOCATION' || e.code === 'OUT_OF_PUNCH_WINDOW'
          || e.code === 'NOT_ENROLLED' || e.code === 'ALREADY_PUNCHED') {
        UI.alertModal('打卡未完成',
          `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
      } else {
        UI.toast(e.message, 'error');
      }
    }
  }

  async function doPunch(root, o, a) {
    const fix = fixByActivity[a.id];
    if (!fix) { UI.toast('请先获取当前定位', 'warn'); return; }
    if (Date.now() - fix.fixTs > 300000) {
      UI.toast('定位已过期，不能用旧位置打卡，请重新获取', 'error');
      return;
    }
    try {
      const res = await Api.post(`/offender/activities/${a.id}/punch`, {
        fixTime: localIso(new Date(fix.fixTs)),
        lat: fix.lat,
        lng: fix.lng,
      });
      delete fixByActivity[a.id];
      await UI.alertModal(res.withinRange ? '打卡成功' : '打卡已记录·位置异常',
        `<div style="font-size:14px;line-height:1.8">${UI.esc(res.message)}</div>`,
        res.withinRange ? '✅' : '⚠️');
      render(root, o);
    } catch (e) {
      if (e.code === 'STALE_LOCATION' || e.code === 'OUT_OF_PUNCH_WINDOW') {
        UI.alertModal('打卡被服务端拒绝',
          `<div style="font-size:13.5px;line-height:1.7">${UI.esc(e.message)}</div>`, '⛔');
      } else {
        UI.toast(e.message, 'error');
      }
    }
  }

  function acquireGps(a) {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        UI.toast('当前环境不支持 GPS，请用模拟定位按钮演示', 'warn');
        return resolve();
      }
      navigator.geolocation.getCurrentPosition((pos) => {
        const ageSec = Math.round((Date.now() - pos.coords.timestamp) / 1000);
        if (ageSec > 300) {
          UI.toast('GPS 返回的是缓存旧位置，请重新定位', 'error');
          return resolve();
        }
        fixByActivity[a.id] = {
          lat: pos.coords.latitude, lng: pos.coords.longitude, fixTs: Date.now(), mode: 'gps',
        };
        UI.toast('GPS 定位成功（精度 ±' + Math.round(pos.coords.accuracy) + ' 米）', 'success');
        resolve();
      }, () => { UI.toast('GPS 获取失败，可改用模拟定位演示', 'error'); resolve(); },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });
    });
  }

  global.ActivityPanel = { mount };
})(window);
