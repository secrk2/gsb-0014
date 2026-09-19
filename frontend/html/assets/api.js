/* API 封装：统一 Token、错误结构（ok/code/message/data） */
(function (global) {
  const TOKEN_KEY = 'jwt_token';
  const SESSION_KEY = 'jwt_session';

  const Api = {
    getToken() { return localStorage.getItem(TOKEN_KEY) || ''; },
    setSession(login) {
      localStorage.setItem(TOKEN_KEY, login.token);
      localStorage.setItem(SESSION_KEY, JSON.stringify({
        userId: login.userId, username: login.username, realName: login.realName,
        role: login.role, roleLabel: login.roleLabel,
        officeId: login.officeId, officeName: login.officeName,
        offenderId: login.offenderId,
      }));
    },
    getSession() {
      try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); }
      catch { return null; }
    },
    clear() {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(SESSION_KEY);
    },

    async request(method, path, body) {
      const headers = { 'Content-Type': 'application/json' };
      const token = Api.getToken();
      if (token) headers.Authorization = 'Bearer ' + token;

      let resp, payload;
      try {
        resp = await fetch('/api' + path, {
          method,
          headers,
          body: body ? JSON.stringify(body) : undefined,
        });
      } catch (networkErr) {
        // 断网：fetch 直接 reject
        const err = new Error('网络不可用，请求未发出');
        err.code = 'NETWORK_OFFLINE';
        err.offline = true;
        throw err;
      }

      try {
        payload = await resp.json();
      } catch {
        const err = new Error('服务响应异常（HTTP ' + resp.status + '）');
        err.code = 'BAD_RESPONSE';
        err.status = resp.status;
        throw err;
      }

      if (resp.status === 401) {
        Api.clear();
        const err = new Error(payload.message || '登录已失效，请重新登录');
        err.code = 'UNAUTHORIZED';
        err.status = 401;
        throw err;
      }
      if (!payload.ok) {
        const err = new Error(payload.message || '请求失败');
        err.code = payload.code;
        err.status = resp.status;
        throw err;
      }
      return payload.data;
    },

    get(path) { return Api.request('GET', path); },
    post(path, body) { return Api.request('POST', path, body || {}); },
    put(path, body) { return Api.request('PUT', path, body || {}); },
  };

  global.Api = Api;
})(window);
