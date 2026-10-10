import { useSyncExternalStore } from 'react';

let now = Date.now();
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | undefined;

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (!timer) {
    timer = setInterval(() => {
      now = Date.now();
      listeners.forEach(update => update());
    }, 1000);
  }

  return () => {
    listeners.delete(listener);
    if (!listeners.size && timer) {
      clearInterval(timer);
      timer = undefined;
    }
  };
}

/** One shared clock keeps expiry indicators current without a timer for every card. */
export function useNow() {
  // A fixed server snapshot prevents timestamp differences during static-page hydration.
  return useSyncExternalStore(subscribe, () => now, () => 0);
}
