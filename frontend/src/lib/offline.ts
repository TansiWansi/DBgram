import { Platform } from "react-native";
import { errorMessage, request } from "./api";
type Event = {
  eventId: string;
  postId: string;
  type: "LIKE" | "UNLIKE" | "SAVE" | "UNSAVE" | "VIEW";
};
let owner: string | null = null;
let pending: Event[] = [];
let syncing = false;
let issue = "";
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((listener) => listener());
function persist() {
  if (Platform.OS === "web" && owner) {
    try {
      localStorage.setItem(`dbgram.queue.${owner}`, JSON.stringify(pending));
    } catch {
      issue =
        "Your device could not save pending actions. Keep this page open to retry.";
    }
  }
  emit();
}
export function configureQueue(userId: string | null) {
  if (owner === userId) return;
  owner = userId;
  pending = [];
  issue = "";
  if (userId && Platform.OS === "web") {
    try {
      pending = JSON.parse(
        localStorage.getItem(`dbgram.queue.${owner}`) || "[]",
      );
    } catch { }
  }
  emit();
  if (userId) void syncQueue();
}
export function queueAction(postId: string, type: Event["type"]) {
  if (!owner) throw new Error("Sign in before saving an offline action.");
  const eventId =
    typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === "x" ? r : (r & 3) | 8).toString(16);
      });
  pending.push({ eventId, postId, type });
  persist();
}
export function subscribeQueue(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
export const queueStatus = () => ({
  count: pending.length,
  syncing,
  issue,
  online:
    Platform.OS !== "web" ||
    typeof navigator === "undefined" ||
    navigator.onLine,
});
export async function syncQueue() {
  if (
    !owner ||
    syncing ||
    !pending.length ||
    (Platform.OS === "web" && !navigator.onLine)
  )
    return;
  const currentOwner = owner;
  const batch = pending.slice(0, 100);
  syncing = true;
  issue = "";
  emit();
  try {
    const result = await request("/sync", {
      method: "POST",
      body: { events: batch },
    });
    if (owner !== currentOwner) return;
    const handled = new Set(
      (result || [])
        .filter((v: any) =>
          ["applied", "duplicate", "rejected"].includes(v.status),
        )
        .map((v: any) => v.eventId),
    );
    pending = pending.filter((event) => !handled.has(event.eventId));
    const rejected = (result || []).filter((v: any) => v.status === "rejected");
    if (rejected.length)
      issue = `${rejected.length} pending action(s) couldn't be applied: ${rejected[0].message}`;
    persist();
  } catch (e) {
    if (owner === currentOwner) issue = errorMessage(e);
  } finally {
    syncing = false;
    emit();
  }
}
