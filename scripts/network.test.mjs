import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolveApiConfiguration, resolveServerUrl, resolveUploadLocation } from '../frontend/src/lib/network.ts';

test('the same web build follows the domain where it is hosted', () => {
  assert.deepEqual(resolveApiConfiguration(undefined, true), { root: '/api' });
  assert.deepEqual(resolveApiConfiguration('  ', true), { root: '/api' });
  for (const origin of ['https://dbgram.example.com', 'https://staging.example.net', 'http://localhost:8787']) {
    assert.equal(new URL(resolveApiConfiguration(undefined, true).root + '/feed/page', origin).origin, origin);
    assert.equal(new URL(resolveServerUrl('/api', '/api/media/asset?signature=proof'), origin).origin, origin);
    assert.equal(resolveUploadLocation('/api', '/api/uploads/asset', origin), origin + '/api/uploads/asset');
  }
});

test('native builds accept configured domains and clearly reject missing or relative endpoints', () => {
  assert.ok(resolveApiConfiguration(undefined, false).error);
  assert.ok(resolveApiConfiguration('/api', false).error);
  assert.equal(resolveApiConfiguration(' https://api.example.com/api/// ', false).root, 'https://api.example.com/api');
  assert.equal(resolveServerUrl('https://api.example.com/api', '/api/media/asset?signature=proof'), 'https://api.example.com/api/media/asset?signature=proof');
});

test('bad configuration and unsafe upload destinations cannot receive auth tokens', () => {
  for (const value of ['//elsewhere.example/api', 'ftp://host/api', 'https://user:password@host/api', 'https://host/api?token=x', 'https://host/api#fragment', '/api\\evil', 'not a URL'])
    assert.ok(resolveApiConfiguration(value, true).error, value);
  for (const value of ['https://elsewhere.example/api/uploads/asset', '//elsewhere.example/api/uploads/asset', '/other/asset', '/api/uploads/asset?token=x'])
    assert.throws(() => resolveUploadLocation('https://api.example.com/api', value));
  assert.equal(resolveServerUrl('/api', '//elsewhere.example/media'), '');
});
