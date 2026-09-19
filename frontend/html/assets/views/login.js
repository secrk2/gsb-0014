/* 登录页 */
(function (global) {
  const Views = global.Views || (global.Views = {});

  const DEMO = [
    { user: 'jiandu', label: '监管员（区司法局，全区只读）' },
    { user: 'ganqingshan', label: '青山司法所干警（本所）' },
    { user: 'gancheng', label: '城关司法所干警（本所）' },
    { user: 'ganlonghu', label: '龙湖司法所干警（本所）' },
    { user: 'obj4', label: '矫正对象·陈大山（越界/离线场景）' },
    { user: 'obj5', label: '矫正对象·杨春生（今日应报到）' },
  ];

  Views.login = function (root) {
    root.innerHTML = `
      <div class="login-wrap">
        <form class="login-card" id="login-form">
          <div class="brand">
            <div class="emblem">⚖</div>
            <h1>矫务通</h1>
            <div class="tagline">区司法局社区矫正管理系统</div>
          </div>
          <div class="login-error" id="login-err" style="display:none"></div>
          <div class="field">
            <label>账号</label>
            <input class="input" id="login-username" autocomplete="username" placeholder="请输入账号" />
          </div>
          <div class="field">
            <label>密码</label>
            <input class="input" id="login-password" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </div>
          <button class="btn primary" type="submit" style="width:100%;padding:11px;font-size:15px" id="login-btn">登 录</button>
          <div class="login-hint">
            <div style="margin-bottom:6px">演示账号（密码统一 <code>123456</code>，点击自动填充）：</div>
            ${DEMO.map((d) => `
              <div class="acct">
                <button type="button" data-user="${d.user}"><code>${d.user}</code> ${UI.esc(d.label)}</button>
              </div>`).join('')}
          </div>
        </form>
      </div>`;

    root.querySelectorAll('.acct button').forEach((b) => {
      b.onclick = () => {
        root.querySelector('#login-username').value = b.dataset.user;
        root.querySelector('#login-password').value = '123456';
      };
    });

    root.querySelector('#login-form').onsubmit = async (e) => {
      e.preventDefault();
      const username = root.querySelector('#login-username').value.trim();
      const password = root.querySelector('#login-password').value;
      const errBox = root.querySelector('#login-err');
      const btn = root.querySelector('#login-btn');
      errBox.style.display = 'none';
      btn.disabled = true;
      btn.textContent = '登录中…';
      try {
        const login = await Api.post('/auth/login', { username, password });
        Api.setSession(login);
        UI.toast('登录成功，欢迎 ' + login.realName, 'success');
        location.hash = login.role === 'OFFENDER' ? '#/offender' : '#/dashboard';
      } catch (err) {
        errBox.style.display = 'block';
        errBox.textContent = err.message || '登录失败';
        btn.disabled = false;
        btn.textContent = '登 录';
      }
    };

    setTimeout(() => root.querySelector('#login-username').focus(), 50);
  };
})(window);
