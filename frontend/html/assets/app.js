/* 路由与应用外壳 */
(function (global) {
  const app = document.getElementById('app');

  const routes = [
    { re: /^#\/login$/, view: 'login' },
    { re: /^#\/?$/, view: 'dashboard' },
    { re: /^#\/dashboard$/, view: 'dashboard' },
    { re: /^#\/objects$/, view: 'objects' },
    { re: /^#\/objects\/(\d+)$/, view: 'detail' },
    { re: /^#\/monitor(?:\/(\d+))?$/, view: 'monitor' },
    { re: /^#\/leaves$/, view: 'leaves' },
    { re: /^#\/welfare$/, view: 'welfare' },
    { re: /^#\/offender$/, view: 'offender' },
  ];

  function currentRoute() {
    const hash = location.hash || '#/';
    for (const r of routes) {
      const m = hash.match(r.re);
      if (m) return { view: r.view, params: m.slice(1) };
    }
    return { view: 'notfound', params: [] };
  }

  function navItems(role) {
    if (role === 'OFFENDER') {
      return [{ hash: '#/offender', icon: '📱', label: '我的矫正', view: 'offender' }];
    }
    return [
      { hash: '#/dashboard', icon: '🎯', label: '矫务作战台', view: 'dashboard' },
      { hash: '#/monitor', icon: '🛰️', label: '定位监控', view: 'monitor' },
      { hash: '#/leaves', icon: '📝', label: '请销假审批', view: 'leaves' },
      { hash: '#/welfare', icon: '🤝', label: '公益活动', view: 'welfare' },
      { hash: '#/objects', icon: '🗂️', label: '对象档案', view: 'objects' },
    ];
  }

  function renderShell(activeView) {
    const s = Api.getSession();
    const items = navItems(s.role);
    return `
      <header class="topbar">
        <div class="logo"><span class="mark">矫</span>矫务通</div>
        <div class="spacer"></div>
        <div class="who">
          <span class="who-name">${UI.esc(s.realName)}</span>
          <span class="role-chip">${UI.esc(s.roleLabel)}${s.officeName ? ' · ' + UI.esc(s.officeName) : ''}</span>
          <button class="btn-logout" id="btn-logout">退出</button>
        </div>
      </header>
      <div class="layout">
        <nav class="sidebar">
          ${items.map((it) => `
            <button class="nav-item ${it.view === activeView ? 'active' : ''}" data-hash="${it.hash}">
              <span class="ico">${it.icon}</span><span class="label">${it.label}</span>
            </button>`).join('')}
          <div class="nav-hint">区司法局社区矫正<br/>自研系统 v1.0</div>
        </nav>
        <main class="main" id="view-root"></main>
      </div>`;
  }

  async function render() {
    const session = Api.getSession();
    const route = currentRoute();

    if (!session) {
      if (route.view !== 'login') { location.hash = '#/login'; return; }
      Views.login(app);
      return;
    }
    if (route.view === 'login') {
      location.hash = session.role === 'OFFENDER' ? '#/offender' : '#/dashboard';
      return;
    }
    if (route.view === 'notfound') {
      app.innerHTML = renderShell('') + '';
      document.getElementById('view-root').innerHTML = NotFound();
      bindShell();
      return;
    }
    // 对象账号只能进手机端
    if (session.role === 'OFFENDER' && route.view !== 'offender') {
      location.hash = '#/offender';
      return;
    }
    if (session.role !== 'OFFENDER' && route.view === 'offender') {
      location.hash = '#/dashboard';
      return;
    }

    app.innerHTML = renderShell(route.view);
    bindShell();
    const root = document.getElementById('view-root');
    const view = Views[route.view];
    try {
      await view(root, ...route.params);
    } catch (e) {
      if (e.code === 'UNAUTHORIZED') {
        Api.clear();
        location.hash = '#/login';
        return;
      }
      root.innerHTML = ErrorState('页面加载失败', e.message, 'error');
    }
  }

  function bindShell() {
    document.getElementById('btn-logout').onclick = async () => {
      try { await UI.confirmModal({ title: '退出登录', bodyHtml: '确定退出当前账号？', confirmText: '退出' }); }
      catch { return; }
      Api.clear();
      location.hash = '#/login';
    };
    document.querySelectorAll('.nav-item[data-hash]').forEach((el) => {
      el.onclick = () => { location.hash = el.dataset.hash; };
    });
  }

  function NotFound() {
    return `<div class="state-box"><div class="ico">🧭</div><h3>页面不存在</h3>
      <p>请从左侧菜单进入矫务作战台或对象档案。</p></div>`;
  }

  function ErrorState(title, message, icon) {
    return `<div class="state-box"><div class="ico">${icon === 'error' ? '⚠️' : '🚫'}</div>
      <h3>${UI.esc(title)}</h3><p>${UI.esc(message || '未知错误')}</p></div>`;
  }

  global.AppRender = render;
  global.ErrorState = ErrorState;

  window.addEventListener('hashchange', render);
  if (!location.hash) location.hash = '#/';
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})(window);
