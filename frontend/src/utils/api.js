function readCookie(name) {
  const prefix = `${name}=`;
  const cookie = document.cookie.split('; ').find((item) => item.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : '';
}

async function ensureCsrfToken() {
  await fetch('/api/csrf', { credentials: 'same-origin' });
}

export async function api(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    await ensureCsrfToken();
  }

  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const token = readCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
  const text = await response.text();
  let payload = {};
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { error: text };
    }
  }
  if (!response.ok) {
    const error = new Error(payload.error || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return payload;
}

export async function resolveLatestGame(game) {
  let latest = game;
  const seenGameIds = new Set(game.gameId ? [game.gameId] : []);
  while (latest.rematchGameId) {
    const nextGameId = latest.rematchGameId;
    if (seenGameIds.has(nextGameId)) {
      throw new Error(`Rematch chain contains a cycle at game ${nextGameId}`);
    }
    seenGameIds.add(nextGameId);
    latest = await api(`/api/games/${encodeURIComponent(nextGameId)}`);
  }
  return latest;
}
