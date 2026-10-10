import { useCallback, useEffect, useState } from 'react';
import { errorMessage, request } from './api';
import { useAuth } from './auth';
type Result<T> = {
  key: string;
  data: T | null;
  error: string;
};

/** Each result belongs to both a route and an account; stale account data never renders. */
export function useResource<T = any>(requestedPath: string | null) {
  const { user } = useAuth();
  const path = user ? requestedPath : null;
  const [result, setResult] = useState<Result<T> | null>(null);
  const [revision, setRevision] = useState(0);

  // Invalidate during render, before effects run, so a previous account cannot flash on screen.
  const key = `${user?.id || ''}:${path || ''}:${revision}`;
  const visible = result?.key === key ? result : null;
  const reload = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    if (!path) {
      return;
    }

    let active = true;
    request<T>(path)
      .then(data => {
        if (active) {
          setResult({ key, data, error: '' });
        }
      })
      .catch(error => {
        if (active) {
          setResult({ key, data: null, error: errorMessage(error) });
        }
      });

    return () => {
      active = false;
    };
  }, [key, path]);

  const setData = useCallback((data: T | null) => setResult({ key, data, error: '' }), [key]);
  return {
    data: path ? visible?.data || null : null,
    setData,
    loading: !!path && !visible,
    error: visible?.error || '',
    reload,
  };
}
