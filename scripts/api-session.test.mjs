import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from '../frontend/node_modules/typescript/lib/typescript.js';

const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText;
const dataUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const network = dataUrl(compile(await readFile(new URL('../frontend/src/lib/network.ts', import.meta.url), 'utf8')));
const source = (await readFile(new URL('../frontend/src/lib/api.ts', import.meta.url), 'utf8'))
  .replace('import { Platform } from "react-native";', 'const Platform = { OS: "web" };')
  .replace('import * as SecureStore from "expo-secure-store";', 'const SecureStore = {};')
  .replace('from "./network"', `from "${network}"`);
const moduleUrl = dataUrl(compile(source));
let serial = 0;
const fresh = () => import(`${moduleUrl}#case-${serial++}`);
const payload = data => new Response(JSON.stringify({ success: true, data }), { headers: { 'Content-Type': 'application/json' } });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
const mockFetch = (context, replacement) => { const original = globalThis.fetch; globalThis.fetch = replacement; context.after(() => { globalThis.fetch = original; }); };

test('a slow account A response cannot return data after account B signs in', async context => {
  const api = await fresh();
  await api.rememberSession({ userId: 'account-a', accessToken: 'token-a' });
  const response = deferred();
  mockFetch(context, () => response.promise);
  const request = api.request('/users/me');
  await api.forgetSession();
  await api.rememberSession({ userId: 'account-b', accessToken: 'token-b' });
  response.resolve(payload({ private: 'account-a-data' }));
  await assert.rejects(request, /account changed/);
  assert.equal(api.token(), 'token-b');
});

test('changing accounts while a response body downloads also rejects the old result', async context => {
  const api = await fresh();
  await api.rememberSession({ userId: 'account-a', accessToken: 'token-a' });
  const started = deferred(); const body = deferred();
  mockFetch(context, async () => ({ status: 200, ok: true, text: () => { started.resolve(); return body.promise; } }));
  const request = api.request('/posts/private');
  await started.promise;
  await api.forgetSession();
  await api.rememberSession({ userId: 'account-b', accessToken: 'token-b' });
  body.resolve(JSON.stringify({ success: true, data: { private: 'account-a-data' } }));
  await assert.rejects(request, /account changed/);
});

test('late refresh responses cannot replace the new account token', async context => {
  const api = await fresh();
  await api.rememberSession({ userId: 'account-a', accessToken: 'token-a' });
  const response = deferred();
  mockFetch(context, () => response.promise);
  const refresh = api.refreshSession();
  await api.forgetSession();
  await api.rememberSession({ userId: 'account-b', accessToken: 'token-b' });
  response.resolve(payload({ userId: 'account-a', accessToken: 'late-token-a' }));
  await assert.rejects(refresh, /account changed/);
  assert.equal(api.token(), 'token-b');
});

test('expired access tokens rotate once and retry with the same account', async context => {
  const api = await fresh();
  await api.rememberSession({ userId: 'account-a', accessToken: 'expired-token' });
  const calls = [];
  mockFetch(context, async (url, options) => {
    calls.push({ url, authorization: options.headers.Authorization });
    if (url.endsWith('/auth/refresh')) return payload({ userId: 'account-a', accessToken: 'rotated-token' });
    return options.headers.Authorization === 'Bearer rotated-token' ? payload({ id: 'account-a' }) : new Response('{}', { status: 401 });
  });
  assert.deepEqual(await api.request('/users/me'), { id: 'account-a' });
  assert.equal(calls.length, 3);
  assert.equal(calls[2].authorization, 'Bearer rotated-token');
});

test('logout can revoke a refresh session when its access token has expired', async context => {
  const api = await fresh();
  await api.rememberSession({ userId: 'account-a', accessToken: 'expired-token' });
  let revoked = false;
  mockFetch(context, async (url, options) => {
    if (url.endsWith('/auth/refresh')) return payload({ userId: 'account-a', accessToken: 'rotated-token' });
    if (url.endsWith('/auth/logout') && options.headers.Authorization === 'Bearer rotated-token') { revoked = true; return payload({}); }
    return new Response('{}', { status: 401 });
  });
  await api.request('/auth/logout', { method: 'POST', body: {} });
  assert.equal(revoked, true);
});
