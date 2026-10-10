import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import { resolveApiConfiguration, resolveServerUrl } from "./network";

const configuration = resolveApiConfiguration(process.env.EXPO_PUBLIC_API_URL, Platform.OS === "web");
export const API_ROOT = configuration.root;
export const API_CONFIGURATION_ERROR = configuration.error;
let accessToken: string | null = null;
let sessionUser: string | null = null;
let sessionGeneration = 0;
let refreshInFlight: Promise<any> | null = null;
let expired = () => { };
export const setExpiredHandler = (handler: () => void) => {
  expired = handler;
};
export class ApiError extends Error {
  constructor(
    message: string,
    public status = 0,
  ) {
    super(message);
  }
}
export const token = () => accessToken;
export function mediaUrl(url?: string) {
  return url ? resolveServerUrl(API_ROOT, url) : "";
}
export async function rememberSession(data: any) {
  if (sessionUser !== data.userId) sessionGeneration += 1;
  sessionUser = data.userId;
  accessToken = data.accessToken;
  if (Platform.OS !== "web" && data.refreshToken)
    await SecureStore.setItemAsync("dbgram.refresh", data.refreshToken);
}
export async function forgetSession() {
  sessionGeneration += 1;
  sessionUser = null;
  accessToken = null;
  if (Platform.OS !== "web")
    await SecureStore.deleteItemAsync("dbgram.refresh");
}
export async function refreshSession(): Promise<any> {
  if (refreshInFlight) return refreshInFlight;
  const generation = sessionGeneration;
  const user = sessionUser;
  refreshInFlight = (async () => {
    const refreshToken =
      Platform.OS !== "web"
        ? await SecureStore.getItemAsync("dbgram.refresh")
        : undefined;
    const data = await request(
      "/auth/refresh",
      { method: "POST", body: { refreshToken } },
      false,
    );
    if (sessionGeneration !== generation || (user && data.userId !== user))
      throw new ApiError("Your account changed. Please retry this action.", 401);
    await rememberSession(data);
    return data;
  })().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}
type Options = Omit<RequestInit, "body"> & { body?: any };
export async function request<T = any>(
  path: string,
  options: Options = {},
  retry = true,
): Promise<T> {
  if (API_CONFIGURATION_ERROR) throw new ApiError(API_CONFIGURATION_ERROR);
  const generation = sessionGeneration;
  const abort = new AbortController();
  const timeout = setTimeout(() => abort.abort(), 20000);
  try {
    const response = await fetch(API_ROOT + path, {
      ...options,
      credentials: "include",
      signal: abort.signal,
      headers: {
        Accept: "application/json",
        ...(Platform.OS !== "web" ? { "X-Client-Type": "native" } : {}),
        ...(options.body !== undefined
          ? { "Content-Type": "application/json" }
          : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...options.headers,
      },
      body:
        options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
    if (sessionGeneration !== generation && !path.startsWith("/auth/"))
      throw new ApiError("Your account changed. Please retry this action.", 401);
    if (response.status === 401 && retry && !["/auth/login", "/auth/register", "/auth/refresh"].includes(path)) {
      try {
        await refreshSession();
      } catch {
        if (sessionGeneration !== generation) throw new ApiError("Your account changed. Please retry this action.", 401);
        await forgetSession();
        expired();
        throw new ApiError(
          "Your session has expired. Please sign in again.",
          401,
        );
      }
      if (sessionGeneration !== generation) throw new ApiError("Your account changed. Please retry this action.", 401);
      return request<T>(path, options, false);
    }
    const text = await response.text();
    let payload: any;
    try {
      payload = text ? JSON.parse(text) : {};
    } catch {
      throw new ApiError(
        "The server returned an unexpected response. Please try again.",
        response.status,
      );
    }
    if (!response.ok || payload.success === false)
      throw new ApiError(
        payload.message ||
        payload.error ||
        `Request failed (${response.status})`,
        response.status,
      );
    if (sessionGeneration !== generation && !path.startsWith("/auth/"))
      throw new ApiError("Your account changed. Please retry this action.", 401);
    return (
      Object.prototype.hasOwnProperty.call(payload, "data")
        ? payload.data
        : payload
    ) as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(
      error instanceof Error && error.name === "AbortError"
        ? "The server is taking too long. Please try again."
        : "We couldn’t reach the server. Check your connection and try again.",
    );
  } finally {
    clearTimeout(timeout);
  }
}
export const list = (data: any): any[] =>
  Array.isArray(data)
    ? data
    : data?.content || data?.items || data?.posts || [];
export const idOf = (value: any): string => value?.id || value?.userId || "";
export const errorMessage = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
export const safeLink = (url: string) => (/^https?:\/\//i.test(url) ? url : "");



