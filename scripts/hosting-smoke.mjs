import assert from 'node:assert/strict';
const base = (process.env.DBGRAM_TEST_URL || 'http://localhost:8080').replace(/\/$/, '');
const fetchTimed = (path, options = {}) => fetch(base + path, { ...options, signal: AbortSignal.timeout(20000) });
const health = await fetchTimed('/actuator/health');
assert.equal(health.status, 200);
assert.equal((await health.json()).status, 'UP');
console.log('PASS hosted service health');

let index = '';
for (const path of ['/', '/feed', '/login', '/register', '/privacy', '/community?id=example', '/post?id=example', '/messages', '/analytics']) {
  const response = await fetchTimed(path);
  assert.equal(response.status, 200, `page ${path}`);
  assert.match(response.headers.get('content-type') || '', /text\/html/);
  const html = await response.text();
  assert.match(html, /id="root"/);
  if (path === '/') index = html;
}
assert.equal((await fetchTimed('/login', { method: 'HEAD' })).status, 200);
console.log('PASS exported frontend routes and deep links');

const assets = [...index.matchAll(/<script[^>]+src="([^"]+)"/g)].map(match => match[1]);
assert.ok(assets.length, 'page must contain its JavaScript bundle');
for (const asset of assets) {
  const url = new URL(asset, base);
  assert.equal(url.origin, new URL(base).origin);
  const response = await fetchTimed(url.pathname);
  assert.equal(response.status, 200, `asset ${url.pathname}`);
  assert.match(response.headers.get('content-type') || '', /javascript/);
}
console.log('PASS public frontend assets use the same origin');

for (const path of ['/api/users/me', '/api/feed/page', '/api/acl/lists', '/api/not-a-page', '/actuator/info', '/api/keys/example/bundle']) {
  const response = await fetchTimed(path);
  assert.ok([401, 403].includes(response.status), `private route ${path} returned ${response.status}`);
  assert.doesNotMatch(response.headers.get('content-type') || '', /text\/html/);
}
// Java rejects unauthenticated writes; the static preview rejects the method.
assert.ok([401, 405].includes((await fetchTimed('/privacy', { method: 'POST' })).status));
console.log('PASS public frontend routing preserves API and management restrictions');
