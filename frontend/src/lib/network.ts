export type ApiConfiguration = { root: string; error?: string };

/** Web deployments use their current domain; native builds need an explicit server URL. */
export function resolveApiConfiguration(value: string | undefined, web: boolean): ApiConfiguration {
  const configured = value?.trim();
  if (!configured) {
    return web
      ? { root: '/api' }
      : { root: '', error: 'Set EXPO_PUBLIC_API_URL to your server URL before building the mobile app.' };
  }
  if (/^\/(?!\/)/.test(configured) && !/[\\?#]/.test(configured)) {
    return web
      ? { root: configured.replace(/\/+$/, '') || '/api' }
      : { root: '', error: 'Mobile apps require an absolute EXPO_PUBLIC_API_URL, such as https://dbgram.example.com/api.' };
  }
  try {
    const url = new URL(configured);
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password || url.search || url.hash)
      throw new Error('Invalid API URL');
    return { root: url.toString().replace(/\/+$/, '') };
  } catch {
    return { root: '', error: 'EXPO_PUBLIC_API_URL must be an HTTP(S) server URL without credentials, a query, or a fragment.' };
  }
}

/** Server-owned paths resolve against the API origin, including signed media URLs. */
export function resolveServerUrl(root: string, path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  if (!/^\/(?!\/)/.test(path) || path.includes('\\')) return '';
  if (/^https?:\/\//i.test(root)) return new URL(path, root).toString();
  return path;
}

/** Never forward the bearer token to an upload location on a different origin. */
export function resolveUploadLocation(root: string, location: string, webOrigin?: string): string {
  const base = /^https?:\/\//i.test(root) ? root : new URL(root, webOrigin).toString();
  const upload = new URL(location, base);
  if (upload.origin !== new URL(base).origin || !/\/api\/uploads\/[^/]+$/.test(upload.pathname) || upload.search || upload.hash || upload.username || upload.password)
    throw new Error('The upload server returned an invalid upload location.');
  return upload.toString();
}
