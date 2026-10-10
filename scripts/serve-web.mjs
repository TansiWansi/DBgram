import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Shared local preview. Production is served by Caddy through compose.yml.
const root = path.resolve(fileURLToPath(new URL('../frontend/dist/', import.meta.url)));
const port = Number(process.env.WEB_PORT || 8787);
const host = process.env.WEB_HOST || '0.0.0.0';
const backend = new URL(process.env.BACKEND_URL || 'http://localhost:8080');
if (!['http:', 'https:'].includes(backend.protocol)
    || backend.username || backend.password || backend.search || backend.hash
    || backend.pathname !== '/') {
  throw new Error('BACKEND_URL must be an HTTP(S) server origin without credentials.');
}
const transport = backend.protocol === 'https:' ? https : http;
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
};

function proxyRequest(req, res, url) {
  const headers = { ...req.headers };
  // Only this proxy supplies forwarding metadata. Keeping the browser's Host
  // makes Spring's same-origin checks work without baking in a Wi-Fi IP.
  for (const name of Object.keys(headers)) {
    if (name === 'forwarded' || name.startsWith('x-forwarded-')) {
      delete headers[name];
    }
  }
  headers['x-forwarded-host'] = req.headers.host;
  headers['x-forwarded-proto'] = 'http';
  headers['x-forwarded-for'] = req.socket.remoteAddress;

  // The destination always comes from configuration, never from a request URL.
  const target = new URL(backend);
  target.pathname = url.pathname;
  target.search = url.search;
  const proxied = transport.request(target, { method: req.method, headers }, upstream => {
    res.writeHead(upstream.statusCode, upstream.headers);
    upstream.pipe(res);
  });
  proxied.setTimeout(30000, () => proxied.destroy());
  proxied.on('error', () => {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, message: 'The API server is not running.' }));
  });
  req.on('aborted', () => proxied.destroy());
  req.pipe(proxied);
}

const server = http.createServer((req, res) => {
  if (!req.headers.host || !req.url.startsWith('/') || req.url.startsWith('//')) {
    res.writeHead(400);
    res.end();
    return;
  }
  let url;
  try {
    url = new URL(req.url, 'http://localhost');
  } catch {
    res.writeHead(400);
    res.end();
    return;
  }
  // Reserved server routes must keep the API's authentication and JSON responses.
  // Falling back to index.html here would make a denied route look successful.
  const apiRoute = url.pathname === '/api' || url.pathname.startsWith('/api/');
  const managementRoute = url.pathname === '/actuator' || url.pathname.startsWith('/actuator/');
  if (apiRoute || managementRoute) {
    proxyRequest(req, res, url);
    return;
  }
  if (!['GET', 'HEAD'].includes(req.method)) {
    res.writeHead(405);
    res.end();
    return;
  }
  let decoded;
  try {
    decoded = decodeURIComponent(url.pathname);
  } catch {
    res.writeHead(400);
    res.end();
    return;
  }
  const candidate = path.resolve(root, '.' + decoded);
  if (candidate !== root && !candidate.startsWith(root + path.sep)) {
    res.writeHead(403);
    res.end();
    return;
  }
  const candidates = [
    candidate,
    candidate + '.html',
    path.join(candidate, 'index.html'),
    path.join(root, 'index.html'),
  ];
  const selected = candidates.find(file => fs.existsSync(file) && fs.statSync(file).isFile());
  if (!selected) {
    res.writeHead(404);
    res.end('Run the frontend web export first.');
    return;
  }
  res.writeHead(200, {
    'Content-Type': mime[path.extname(selected)] || 'application/octet-stream',
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': 'no-store',
  });
  if (req.method === 'HEAD') {
    res.end();
  } else {
    fs.createReadStream(selected).pipe(res);
  }
});

server.listen(port, host, () => console.log(`DBgram preview http://${host}:${port}, API upstream ${backend.origin}`));
