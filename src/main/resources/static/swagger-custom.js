(function () {
  function getTokenExpiry(token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000;
    } catch {
      return null;
    }
  }

  function isExpiredOrSoon(token) {
    const exp = getTokenExpiry(token);
    return exp === null || Date.now() > exp - 30_000;
  }

  async function refreshAccessToken() {
    const res = await fetch('/api/auth/token/refresh', {
      method: 'POST',
      credentials: 'include', // httpOnly 쿠키 자동 전송
    });
    if (!res.ok) return null;
    const data = await res.json();
    return data.accessToken ?? null;
  }

  const timer = setInterval(() => {
    if (!window.ui) return;
    clearInterval(timer);

    window.ui.getConfigs().requestInterceptor = async (request) => {
      const auth = window.ui.getState().getIn(['auth', 'authorized', 'bearerAuth']);
      if (!auth) return request;

      const accessToken = auth.getIn(['value']);
      if (!isExpiredOrSoon(accessToken)) return request;

      console.log('[Swagger] Access token 만료 → 갱신 시도');
      const newToken = await refreshAccessToken();

      if (newToken) {
        window.ui.preauthorizeApiKey('bearerAuth', newToken);
        request.headers['Authorization'] = `Bearer ${newToken}`;
        console.log('[Swagger] Access token 갱신 완료');
      } else {
        console.warn('[Swagger] Access token 갱신 실패 (로그인 필요)');
      }

      return request;
    };
  }, 300);
})();
