import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = (process.env.DBGRAM_TEST_URL || 'http://localhost:8080').replace(/\/$/, '');
const tag = randomUUID().slice(0, 8);
let checks = 0;
function pass(name) { checks++; console.log(`PASS ${name}`); }
async function api(path, user, method = 'GET', body, expected = 200) {
  const response = await fetch(base + '/api' + path, { method, headers: { 'Content-Type': 'application/json', 'X-Client-Type': 'native', ...(user ? { Authorization: `Bearer ${user.accessToken}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
  const payload = await response.json();
  assert.ok([expected].flat().includes(response.status), `${method} ${path}: ${response.status} ${payload.message}`);
  return payload.data;
}
const blocks = content => [{ type: 'text', data: { content } }];
async function account(letter) { return api('/auth/register', null, 'POST', { username: `features_${tag}_${letter}`, email: `features_${tag}_${letter}@example.test`, displayName: 'Feature QA', password: `Test-${randomUUID()}!` }, 201); }
const a = await account('a'), b = await account('b'), c = await account('c');
const circle = await api('/acl/lists', a, 'POST', { name: `Circle ${tag}`, userIds: [b.userId, b.userId] });
assert.deepEqual(circle.userIds, [b.userId]);
const privatePost = await api('/posts', a, 'POST', { blocks: blocks('custom circle'), visibility: 'CUSTOM', allowedListIds: [circle.id] }, 201);
await api(`/posts/${privatePost.id}`, b);
await api(`/posts/${privatePost.id}`, c, 'GET', undefined, [403, 404]);
await api(`/acl/lists/${circle.id}`, b, 'PUT', { userIds: [c.userId] }, [403, 404]);
await api(`/acl/lists/${circle.id}`, a, 'PUT', { name: 'A renamed circle', userIds: [c.userId] });
await api(`/posts/${privatePost.id}`, b, 'GET', undefined, [403, 404]);
await api(`/posts/${privatePost.id}`, c);
await api(`/acl/lists/${circle.id}`, a, 'DELETE');
await api(`/posts/${privatePost.id}`, c, 'GET', undefined, [403, 404]);
await api(`/posts/${privatePost.id}`, a);
pass('owned audience lists, membership replacement and immediate access revocation');

await api(`/users/${b.userId}/follow`, a, 'POST', {});
await api(`/users/${b.userId}/close-friend`, a, 'PUT', { enabled: true });
const close = await api('/posts', a, 'POST', { blocks: blocks('close friends'), visibility: 'CLOSE_FRIENDS' }, 201);
await api(`/posts/${close.id}`, b);
await api(`/posts/${close.id}`, c, 'GET', undefined, [403, 404]);
await api(`/users/${b.userId}/close-friend`, a, 'PUT', { enabled: false });
await api(`/posts/${close.id}`, b, 'GET', undefined, [403, 404]);
const publicPost = await api('/posts', a, 'POST', { blocks: blocks('public then private') }, 201);
await api(`/posts/${publicPost.id}`, b);
const revised = await api(`/posts/${publicPost.id}`, a, 'PUT', { blocks: blocks('private now'), expectedVersion: publicPost.currentVersion, visibility: 'PRIVATE' });
assert.equal(revised.visibility, 'PRIVATE');
await api(`/posts/${publicPost.id}`, b, 'GET', undefined, [403, 404]);
pass('close-friend removal and publishing edits enforce the new visibility');

const group = await api('/communities', a, 'POST', { name: 'Cursor QA', slug: `cursor-${tag}` }, 201);
await api(`/communities/${group.id}/join`, b, 'POST', {});
const originals = [];
for (let index = 0; index < 5; index++) originals.push((await api('/posts', a, 'POST', { blocks: blocks(`cursor ${index}`), communityId: group.id, visibility: 'COMMUNITY_ONLY' }, 201)).id);
const feedPath = `/feed/page?mode=discover&size=2&communityId=${group.id}`;
let page = await api(feedPath, b);
assert.equal(page.items.length, 2); assert.ok(page.nextCursor);
await api(feedPath + '&cursor=' + encodeURIComponent(page.nextCursor), c, 'GET', undefined, 400);
const inserted = await api('/posts', a, 'POST', { blocks: blocks('inserted during scroll'), communityId: group.id, visibility: 'COMMUNITY_ONLY' }, 201);
const chronological = [...page.items.map(post => post.id)];
while (page.nextCursor) { page = await api(feedPath + '&cursor=' + encodeURIComponent(page.nextCursor), b); chronological.push(...page.items.map(post => post.id)); }
assert.equal(new Set(chronological).size, 5); assert.deepEqual(new Set(chronological), new Set(originals)); assert.ok(!chronological.includes(inserted.id));
pass('chronological cursor paging survives concurrent insertions and rejects another viewer');

const rankedPath = `/feed/page?mode=ranked&size=2&communityId=${group.id}`;
page = await api(rankedPath, b);
const ranked = [...page.items.map(post => post.id)];
await api(`/posts/${originals[0]}/like`, b, 'POST', {});
const afterRanking = await api('/posts', a, 'POST', { blocks: blocks('after ranked snapshot'), communityId: group.id, visibility: 'COMMUNITY_ONLY' }, 201);
while (page.nextCursor) { page = await api(rankedPath + '&cursor=' + encodeURIComponent(page.nextCursor), b); ranked.push(...page.items.map(post => post.id)); }
assert.equal(new Set(ranked).size, 6); assert.deepEqual(new Set(ranked), new Set([...originals, inserted.id])); assert.ok(!ranked.includes(afterRanking.id));
pass('algorithmic feed snapshots keep ordering stable as engagement changes');

// A collaborator may retain the original author's attachment, but cannot attach somebody else's upload.
async function image(user) {
  const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jGxkAAAAASUVORK5CYII=', 'base64');
  const headers = { Authorization: `Bearer ${user.accessToken}`, 'Tus-Resumable': '1.0.0' };
  const created = await fetch(base + '/api/uploads', { method: 'POST', headers: { ...headers, 'Upload-Length': String(bytes.length), 'Upload-Metadata': `filename ${Buffer.from('collaboration.png').toString('base64')},filetype ${Buffer.from('image/png').toString('base64')}` } });
  assert.equal(created.status, 201);
  const location = new URL(created.headers.get('Location'), base);
  const patched = await fetch(location, { method: 'PATCH', headers: { ...headers, 'Upload-Offset': '0', 'Content-Type': 'application/offset+octet-stream' }, body: bytes });
  assert.equal(patched.status, 204);
  return location.pathname.split('/').pop();
}
const ownImage = await image(a), thirdPartyImage = await image(c);
const collab = await api('/posts', a, 'POST', { blocks: [{ type: 'image', data: { url: `/api/media/${ownImage}`, mediaId: ownImage } }], visibility: 'PRIVATE' }, 201);
const invitation = await api(`/posts/${collab.id}/authors/invite`, a, 'POST', { userId: b.userId, contributionPct: 25 });
await api(`/collaborations/${invitation.id}`, b, 'POST', { accept: true });
const accepted = await api(`/posts/${collab.id}`, b);
assert.equal(accepted.authors.reduce((sum, author) => sum + Number(author.contributionPct), 0), 100);
const coedit = await api(`/posts/${collab.id}`, b, 'PUT', { blocks: [...accepted.blocks, ...blocks('coauthor edit')], expectedVersion: accepted.currentVersion });
assert.equal(coedit.blocks.length, 2);
await api(`/posts/${collab.id}`, b, 'PUT', { blocks: coedit.blocks, visibility: 'PUBLIC' }, 403);
await api(`/posts/${collab.id}`, b, 'PUT', { blocks: [{ type: 'image', data: { url: `/api/media/${thirdPartyImage}` } }] }, 403);
pass('accepted collaboration retains shared media and restricts audience and foreign-upload changes');

const allowedOrigin = process.env.DBGRAM_TEST_ORIGIN || (/localhost|127\.0\.0\.1/.test(base) ? "http://localhost:8787" : base);
const allowed = await fetch(base + '/api/auth/login', { method: 'OPTIONS', headers: { Origin: allowedOrigin, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'Content-Type' } });
assert.equal(allowed.status, 200); assert.equal(allowed.headers.get('Access-Control-Allow-Origin'), allowedOrigin);
const denied = await fetch(base + '/api/auth/login', { method: 'OPTIONS', headers: { Origin: 'https://untrusted.example', 'Access-Control-Request-Method': 'POST' } });
assert.equal(denied.status, 403); assert.equal(denied.headers.get('Access-Control-Allow-Origin'), null);
const info = await fetch(base + '/actuator/info', { headers: { Authorization: `Bearer ${a.accessToken}` } }); assert.equal(info.status, 403);
pass('configured CORS origins and private actuator endpoints');
console.log(`\n${checks} feature integration checks passed against ${base}. Test data prefix: features_${tag}`);

