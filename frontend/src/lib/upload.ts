import { Platform } from "react-native";
import { API_ROOT, API_CONFIGURATION_ERROR, errorMessage, token } from "./api";
import { resolveUploadLocation } from "./network";
export type UploadJob = { file: File; location?: string; offset: number };
function headers(extra: Record<string, string> = {}) {
  return {
    "Tus-Resumable": "1.0.0",
    ...(token() ? { Authorization: `Bearer ${token()}` } : {}),
    ...extra,
  };
}
export async function uploadMedia(
  job: UploadJob,
  onProgress: (percent: number) => void,
) {
  if (Platform.OS !== "web")
    throw new Error(
      "Use a media URL on this device, or upload through the web app.",
    );
  if (!job.file.type.match(/^(image|video|audio)\//))
    throw new Error("Choose an image, video, or audio file.");
  if (API_CONFIGURATION_ERROR) throw new Error(API_CONFIGURATION_ERROR);
  if (job.file.size > 100 * 1024 * 1024)
    throw new Error("Choose a file smaller than 100 MB.");
  const resolveLocation = (location: string) =>
    resolveUploadLocation(API_ROOT, location, window.location.origin);
  if (!job.location) {
    const metadata = `filename ${btoa(unescape(encodeURIComponent(job.file.name)))},filetype ${btoa(job.file.type)}`;
    const result = await fetch(API_ROOT + "/uploads", {
      method: "POST",
      credentials: "include",
      headers: headers({
        "Upload-Length": String(job.file.size),
        "Upload-Metadata": metadata,
      }),
    });
    if (!result.ok)
      throw new Error(
        `Could not start upload (${result.status}). Please sign in again if your session expired.`,
      );
    job.location = result.headers.get("Location") || undefined;
    if (!job.location)
      throw new Error("The upload server did not return an upload location.");
  } else {
    const head = await fetch(resolveLocation(job.location), {
      method: "HEAD",
      credentials: "include",
      headers: headers(),
    });
    if (!head.ok)
      throw new Error("Could not resume this upload. Choose the file again.");
    job.offset = Number(head.headers.get("Upload-Offset") || 0);
  }
  while (job.offset < job.file.size) {
    const body = job.file.slice(job.offset, job.offset + 5 * 1024 * 1024);
    const result = await fetch(resolveLocation(job.location), {
      method: "PATCH",
      credentials: "include",
      headers: headers({
        "Content-Type": "application/offset+octet-stream",
        "Upload-Offset": String(job.offset),
      }),
      body,
    });
    if (!result.ok)
      throw new Error(
        `Upload paused at ${Math.round((job.offset / job.file.size) * 100)}%. Tap resume to continue (${result.status}).`,
      );
    job.offset = Number(
      result.headers.get("Upload-Offset") || job.offset + body.size,
    );
    onProgress(Math.round((job.offset / job.file.size) * 100));
  }
  const id = job.location.split("/").pop();
  return {
    mediaId: id,
    url: `/api/media/${id}`,
    filename: job.file.name,
    mimeType: job.file.type,
  };
}
export { errorMessage };

