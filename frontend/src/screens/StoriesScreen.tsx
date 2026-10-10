import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  AppState,
  Image,
  Platform,
  Pressable,
  ScrollView,
  Text,
  View,
} from "react-native";
import { router, useLocalSearchParams, type Href } from "expo-router";
import { LinearGradient } from "expo-linear-gradient";
import { useEvent, useEventListener } from "expo";
import { useVideoPlayer, VideoView } from "expo-video";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Gate, Shell } from "../components/Shell";
import {
  Avatar,
  Button,
  ErrorNote,
  Field,
  go,
  Icon,
  ResourceState,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage, list, mediaUrl, request } from "../lib/api";
import { useResource } from "../lib/useResource";
import { useNow } from "../lib/useNow";
import { uploadMedia, type UploadJob } from "../lib/upload";

export default function StoriesScreen() {
  const { create, user: author, story } = useLocalSearchParams<{
    create?: string;
    user?: string;
    story?: string;
  }>();
  const { user } = useAuth();
  if (!user || create === "1") {
    return (
      <Shell>
        <Gate>
          <StoryComposer />
        </Gate>
      </Shell>
    );
  }
  return (
    <StoryViewer
      key={`${author || user.id}:${story || ""}`}
      author={author || user.id}
      requestedStory={story}
    />
  );
}

function StoryComposer() {
  const { c, s } = useUI();
  const { user } = useAuth();
  const [caption, setCaption] = useState("");
  const [visibility, setVisibility] = useState("PUBLIC");
  const [media, setMedia] = useState<any>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const job = useRef<UploadJob | null>(null);
  const [canResume, setCanResume] = useState(false);

  async function upload(file?: File) {
    if (file) {
      if (!file.type.startsWith("image/") && !file.type.startsWith("video/")) {
        setError("Choose a photo or video for your story.");
        return;
      }
      job.current = { file, offset: 0 };
    }
    if (!job.current) {
      return;
    }
    setUploading(true);
    setCanResume(false);
    setError("");
    try {
      const uploaded = await uploadMedia(job.current, setProgress);
      const details = await request(`/media/${uploaded.mediaId}/metadata`);
      if (!details.mimeType.startsWith("image/") && !details.mimeType.startsWith("video/")) {
        throw new Error("This file isn’t a supported photo or video.");
      }
      setMedia({
        type: details.mimeType.startsWith("video/") ? "video" : "image",
        data: uploaded,
      });
      setPreview(details.url);
      job.current = null;
    } catch (e) {
      setError(errorMessage(e));
      setCanResume(Boolean(job.current?.location));
    } finally {
      setUploading(false);
    }
  }

  async function publish() {
    setBusy(true);
    setError("");
    try {
      const blocks = [
        media,
        caption.trim() ? { type: "text", data: { content: caption.trim() } } : null,
      ].filter(Boolean);
      const story = await request("/posts", {
        method: "POST",
        body: { postType: "STORY", visibility, blocks },
      });
      router.replace(`/stories?user=${user.id}&story=${story.id}` as Href);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <SectionTitle
        title="Add to your story"
        subtitle="A moment for today. Stories disappear after 24 hours."
      />
      <View style={[s.card, { padding: 24, gap: 24 }]}>
        <ErrorNote message={error} />
        {media?.type === "image" && (
          <View style={{ aspectRatio: 4 / 5, width: "100%", maxHeight: 420, overflow: "hidden", backgroundColor: c.ink }}>
            <Image
              source={{ uri: mediaUrl(preview) }}
              accessibilityLabel="Story preview"
              resizeMode="contain"
              style={{ width: "100%", height: "100%" }}
            />
          </View>
        )}
        {media?.type === "video" && (
          <Text style={s.body}>Video ready: {media.data.filename}</Text>
        )}
        {media && (
          <Button
            title="Remove media"
            quiet
            small
            disabled={busy || uploading}
            onPress={() => {
              setMedia(null);
              setPreview("");
            }}
          />
        )}
        {Platform.OS === "web" && (
          <View style={{ gap: 12, padding: 20, borderWidth: 1, borderStyle: "dashed", borderColor: c.lineStrong, borderRadius: 4 }}>
            <Text style={s.label}>Choose a photo or video</Text>
            <input
              type="file"
              accept="image/*,video/*"
              aria-label="Story photo or video"
              disabled={uploading || busy}
              style={{ color: c.text, maxWidth: "100%", minHeight: 44, fontSize: 14 }}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) {
                  void upload(file);
                }
              }}
            />
            {uploading && <Text style={s.body}>Uploading {progress}%…</Text>}
            {canResume && (
              <Button
                title="Resume story upload"
                small
                disabled={uploading}
                onPress={() => void upload()}
              />
            )}
          </View>
        )}
        <Field
          label={media ? "Add a caption" : "Or share a thought"}
          placeholder="What’s happening in your world?"
          multiline
          maxLength={2000}
          value={caption}
          onChangeText={setCaption}
        />
        <View style={{ gap: 12 }}>
          <Text style={s.label}>Story audience</Text>
          <View style={[s.row, { flexWrap: "wrap" }]}>
            {[
              ["PUBLIC", user.isPrivate ? "Approved followers" : "Everyone"],
              ...(!user.isPrivate ? [["FOLLOWERS_ONLY", "Followers"]] : []),
              ["CLOSE_FRIENDS", "Close friends"],
              ["PRIVATE", "Only me"],
            ].map(([value, label]) => (
              <Tag
                key={value}
                text={label}
                active={visibility === value}
                onPress={() => setVisibility(value)}
              />
            ))}
          </View>
          <Text style={{ color: c.dim, fontSize: 13, lineHeight: 20 }}>
            {user.isPrivate
              ? "Your private account limits every story to approved followers. A narrower audience still applies."
              : "Your account privacy and selected audience both apply to this story."}
          </Text>
        </View>
        <Button
          title="Share story"
          icon="plus"
          busy={busy}
          disabled={uploading || (!media && !caption.trim())}
          onPress={() => void publish()}
        />
      </View>
    </>
  );
}

function StoryViewer({ author, requestedStory }: { author: string; requestedStory?: string }) {
  const { c } = useUI();
  const { user } = useAuth();
  const resource = useResource(`/stories/user/${author}`);
  const tray = useResource("/stories");
  const stories = list(resource.data);
  const [selection, setIndex] = useState<number | null>(null);
  const requested = stories.findIndex((story) => story.id === requestedStory);
  const unseen = stories.findIndex((story) => !story.seen);
  const index = selection ?? (requested >= 0 ? requested : Math.max(0, unseen));

  function next() {
    if (index + 1 < stories.length) {
      setIndex(index + 1);
      return;
    }
    const groups = list(tray.data);
    const position = groups.findIndex((group) => group.userId === author);
    const following = position >= 0 ? groups[position + 1] : null;
    router.replace((following ? `/stories?user=${following.userId}` : "/") as Href);
  }
  const story = stories[Math.min(index, Math.max(0, stories.length - 1))];

  if (!story) {
    return (
      <View style={{ flex: 1, backgroundColor: c.bg, padding: 32, justifyContent: "center", gap: 24 }}>
        <ResourceState
          resource={resource}
          empty={!stories.length}
          title="This story is no longer available"
          body="It may have expired, been removed, or its audience may have changed."
        />
        <Button title="Back to feed" quiet onPress={() => router.replace("/")} />
      </View>
    );
  }
  return (
    <StorySlide
      key={story.id}
      story={story}
      position={index}
      total={stories.length}
      own={story.author?.id === user.id}
      onNext={next}
      onPrevious={() => setIndex(Math.max(0, index - 1))}
      onDeleted={() => {
        setIndex(0);
        resource.reload();
      }}
    />
  );
}

type StorySlideProps = {
  story: any;
  position: number;
  total: number;
  own: boolean;
  onNext: () => void;
  onPrevious: () => void;
  onDeleted: () => void;
};

function StorySlide({ story, position, total, own, onNext, onPrevious, onDeleted }: StorySlideProps) {
  const { c, s, compact } = useUI();
  const insets = useSafeAreaInsets();
  const now = useNow();
  const media = story.blocks.find((block: any) => block.type === "image" || block.type === "video");
  const caption = story.blocks.find((block: any) => block.type === "text")?.data.content;
  const video = media?.type === "video";
  const [loaded, setLoaded] = useState(!media);
  const [paused, setPaused] = useState(false);
  const [holding, setHolding] = useState(false);
  const [authorized, setAuthorized] = useState(false);
  const [background, setBackground] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState("");
  const [showViewers, setShowViewers] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const viewers = useResource(showViewers ? `/stories/${story.id}/viewers` : null);
  const frozen = paused || holding || background || showViewers || confirmDelete || !loaded || !authorized || Boolean(error);
  const nextRef = useRef(onNext);
  useEffect(() => {
    nextRef.current = onNext;
  }, [onNext]);
  const touch = useRef({ x: 0, started: 0, consumed: false });

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      setBackground(state !== "active");
    });
    const visibility = () => setBackground(document.hidden);
    if (Platform.OS === "web") {
      document.addEventListener("visibilitychange", visibility);
    }
    return () => {
      subscription.remove();
      if (Platform.OS === "web") {
        document.removeEventListener("visibilitychange", visibility);
      }
    };
  }, []);

  useEffect(() => {
    let active = true;
    // Each slide rechecks access before showing prefetched content. Later checks
    // stop a paused story if its owner changes privacy or removes this follower.
    async function authorize(record: boolean) {
      try {
        await request(
          record ? `/stories/${story.id}/view` : `/posts/${story.id}`,
          record ? { method: "POST" } : {},
        );
        if (active) {
          setAuthorized(true);
        }
      } catch (e) {
        if (active) {
          setAuthorized(false);
          setError(errorMessage(e));
        }
      }
    }
    void authorize(true);
    const interval = setInterval(() => void authorize(false), 15000);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [story.id]);

  useEffect(() => {
    if (frozen || video) {
      return;
    }
    let previous = Date.now();
    const timer = setInterval(() => {
      const now = Date.now();
      const duration = media ? 6000 : Math.max(6000, Math.min(30000, (caption?.length || 0) * 70));
      const elapsed = (now - previous) / duration;
      previous = now;
      setProgress((value) => Math.min(1, value + elapsed));
    }, 80);
    return () => clearInterval(timer);
  }, [frozen, video, media, caption]);
  useEffect(() => {
    if (progress >= 1 && !frozen) {
      nextRef.current();
    }
  }, [progress, frozen]);
  useEffect(() => {
    const remaining = Date.parse(story.expiresAt) - Date.now();
    const timer = setTimeout(() => setError("This story has expired."), Math.max(0, remaining));
    return () => clearTimeout(timer);
  }, [story.expiresAt]);

  async function remove() {
    setBusy(true);
    try {
      await request(`/posts/${story.id}`, { method: "DELETE" });
      onDeleted();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: c.bg, alignItems: "center", padding: compact ? 0 : 20, paddingBottom: Math.max(compact ? 0 : 20, insets.bottom) }}>
      <View style={{ flex: 1, width: "100%", maxWidth: 480, backgroundColor: c.ink, borderRadius: compact ? 0 : 4, overflow: "hidden" }}>
        {authorized && !error && media?.type === "image" && (
          <Image
            source={{ uri: mediaUrl(media.data.url) }}
            accessibilityLabel="Story photo"
            resizeMode="contain"
            onLoad={() => setLoaded(true)}
            onError={() => setError("This photo is unavailable. Try reopening the story.")}
            style={{ position: "absolute", width: "100%", height: "100%" }}
          />
        )}
        {authorized && !error && video && (
          <StoryVideo
            url={media.data.url}
            paused={paused || holding || background || showViewers || confirmDelete}
            onReady={() => setLoaded(true)}
            onProgress={setProgress}
            onEnd={onNext}
            onError={() => setError("This video is unavailable. Try reopening the story.")}
          />
        )}
        {authorized && !media && !error && (
          <ScrollView
            style={{ position: "absolute", top: 100, bottom: 84, left: 30, right: 30 }}
            contentContainerStyle={{ flexGrow: 1, justifyContent: "center", paddingVertical: 24 }}
            onTouchStart={() => setHolding(true)}
            onTouchEnd={() => setHolding(false)}
          >
            <Text style={{ color: "#F4F1E9", fontSize: caption?.length > 250 ? 20 : 30, lineHeight: caption?.length > 250 ? 30 : 42, textAlign: "center" }}>
              {caption}
            </Text>
          </ScrollView>
        )}
        {(!loaded || !authorized) && !error && (
          <ActivityIndicator
            accessibilityLabel="Loading story"
            color="#F4F1E9"
            style={{ position: "absolute", top: "45%", alignSelf: "center" }}
          />
        )}
        <View pointerEvents="box-none" style={{ position: "absolute", top: 96, bottom: 160, left: 0, right: 0, flexDirection: "row", justifyContent: "space-between" }}>
          {[["Previous story", onPrevious], ["Next story", onNext]].map(([label, action]) => (
            <Pressable
              key={String(label)}
              accessibilityRole="button"
              accessibilityLabel={String(label)}
              onPress={() => {
                if (!touch.current.consumed) {
                  (action as () => void)();
                }
              }}
              onPressIn={(event) => {
                touch.current = { x: event.nativeEvent.pageX, started: Date.now(), consumed: false };
                setHolding(true);
              }}
              onPressOut={(event) => {
                const distance = event.nativeEvent.pageX - touch.current.x;
                touch.current.consumed = Math.abs(distance) > 60 || Date.now() - touch.current.started > 250;
                setHolding(false);
                if (Math.abs(distance) > 60) {
                  if (distance < 0) {
                    onNext();
                  } else {
                    onPrevious();
                  }
                }
              }}
              style={media ? { flex: 1 } : { width: 28 }}
            />
          ))}
        </View>
        <LinearGradient colors={["rgba(0,0,0,0.72)", "transparent"]} style={{ position: "absolute", top: 0, left: 0, right: 0, padding: 16, paddingBottom: 36 }}>
          <View style={{ flexDirection: "row", gap: 4, marginBottom: 12 }}>
            {Array.from({ length: total }, (_, index) => (
              <View key={index} style={{ height: 3, flex: 1, backgroundColor: "rgba(255,255,255,0.3)", overflow: "hidden" }}>
                <View style={{ height: 3, width: `${index < position ? 100 : index === position ? progress * 100 : 0}%`, backgroundColor: "#FFFFFF" }} />
              </View>
            ))}
          </View>
          <View style={[s.row, { gap: 10 }]}>
            <Avatar user={story.author} size={34} />
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text numberOfLines={1} style={{ color: "#FFFFFF", fontWeight: "600", fontSize: 13 }}>
                {story.author.username}
              </Text>
              <Text style={{ color: "#DDE5D8", fontSize: 11, marginTop: 3 }}>
                {Math.max(1, Math.floor((now - Date.parse(story.publishedAt)) / 60000))}m ago
                {" · "}
                {story.visibility === "CLOSE_FRIENDS" ? "Close friends" : "24h story"}
              </Text>
            </View>
            <StoryAction
              name={paused ? "play" : "pause"}
              label={paused ? "Play story" : "Pause story"}
              onPress={() => setPaused(!paused)}
            />
            <StoryAction name="x" label="Close story" onPress={() => router.replace("/")} />
          </View>
        </LinearGradient>
        {authorized && media && caption && !error && (
          <LinearGradient
            colors={["transparent", "rgba(0,0,0,0.8)"]}
            style={{ position: "absolute", bottom: 70, left: 0, right: 0, padding: 24, paddingTop: 48 }}
          >
            <Text numberOfLines={6} style={{ color: "#FFFFFF", fontSize: 16, lineHeight: 24 }}>
              {caption}
            </Text>
          </LinearGradient>
        )}
        {error && (
          <View style={{ position: "absolute", top: "30%", left: 24, right: 24, gap: 20, padding: 24, backgroundColor: c.panel, borderRadius: 4 }}>
            <ErrorNote message={error} />
            <Button title="Back to feed" quiet onPress={() => router.replace("/")} />
          </View>
        )}
        <View style={{ position: "absolute", bottom: 12, left: 16, right: 16, flexDirection: "row", alignItems: "center", justifyContent: "space-between", backgroundColor: "rgba(0,0,0,0.55)", paddingHorizontal: 8, borderRadius: 4 }}>
          {own ? (
            <>
              <StoryAction name="eye" label="Story viewers" onPress={() => setShowViewers(!showViewers)} />
              <Text style={{ color: "#FFFFFF", fontSize: 12 }}>{story.viewerCount || 0} views</Text>
              <StoryAction name="trash-2" label="Delete story" onPress={() => setConfirmDelete(true)} />
            </>
          ) : (
            <>
              <StoryAction
                name="message-circle"
                label="Message story author"
                onPress={() => go(`/messages?user=${story.author.id}`)}
              />
              <Text style={{ color: "#FFFFFF", fontSize: 12 }}>Hold to pause · tap to continue</Text>
              <StoryAction name="arrow-right" label="Next story" onPress={onNext} />
            </>
          )}
        </View>
        {(showViewers || confirmDelete) && (
          <View style={{ position: "absolute", bottom: 0, left: 0, right: 0, maxHeight: "65%", backgroundColor: c.panel, padding: 24, gap: 16, borderTopWidth: 1, borderColor: c.line }}>
            <View style={[s.row, { justifyContent: "space-between" }]}>
              <Text style={s.label}>{confirmDelete ? "Delete this story?" : "Story viewers"}</Text>
              <Button
                title="Close"
                small
                quiet
                onPress={() => {
                  setShowViewers(false);
                  setConfirmDelete(false);
                }}
              />
            </View>
            {confirmDelete ? (
              <>
                <Text style={s.body}>It will no longer be visible to anyone.</Text>
                <Button title="Delete this story" danger busy={busy} onPress={() => void remove()} />
              </>
            ) : (
              <ScrollView>
                <ResourceState
                  resource={viewers}
                  empty={!list(viewers.data).length}
                  title="No views yet"
                  body="People who view your story will appear here."
                />
                {list(viewers.data).map((person) => (
                  <View key={person.id} style={[s.row, { paddingVertical: 12 }]}>
                    <Avatar user={person} size={34} />
                    <Text style={s.label}>{person.username}</Text>
                  </View>
                ))}
              </ScrollView>
            )}
          </View>
        )}
      </View>
    </View>
  );
}

type StoryActionProps = {
  name: string;
  label: string;
  onPress: () => void;
};

function StoryAction({ name, label, onPress }: StoryActionProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={{ minWidth: 44, minHeight: 44, alignItems: "center", justifyContent: "center" }}
    >
      <Icon name={name} color="#FFFFFF" size={21} />
    </Pressable>
  );
}

type StoryVideoProps = {
  url: string;
  paused: boolean;
  onReady: () => void;
  onProgress: (value: number) => void;
  onEnd: () => void;
  onError: () => void;
};

function StoryVideo({ url, paused, onReady, onProgress, onEnd, onError }: StoryVideoProps) {
  const [muted, setMuted] = useState(true);
  const player = useVideoPlayer(mediaUrl(url), (instance) => {
    instance.muted = true;
    instance.timeUpdateEventInterval = 0.1;
  });
  const { status } = useEvent(player, "statusChange", { status: player.status });
  const callbacks = useRef({ onReady, onProgress, onEnd, onError });
  useEffect(() => {
    callbacks.current = { onReady, onProgress, onEnd, onError };
  }, [onReady, onProgress, onEnd, onError]);
  useEventListener(player, "playToEnd", () => callbacks.current.onEnd());
  useEventListener(player, "timeUpdate", ({ currentTime }) => {
    if (player.duration > 0) {
      callbacks.current.onProgress(Math.min(0.99, currentTime / player.duration));
    }
  });
  useEffect(() => {
    if (status === "error") {
      callbacks.current.onError();
    }
    if (status === "readyToPlay") {
      callbacks.current.onReady();
      if (paused) {
        player.pause();
      } else {
        player.play();
      }
    }
  }, [status, paused, player]);
  function toggleMuted() {
    // Expo's player is an imperative native object, not immutable React state.
    // eslint-disable-next-line react-hooks/immutability
    player.muted = !muted;
    setMuted(!muted);
  }
  return (
    <>
      <VideoView
        player={player}
        nativeControls={false}
        contentFit="contain"
        style={{ position: "absolute", width: "100%", height: "100%" }}
      />
      <View style={{ position: "absolute", right: 16, top: 100, zIndex: 2 }}>
        <StoryAction
          name={muted ? "volume-x" : "volume-2"}
          label={muted ? "Unmute story" : "Mute story"}
          onPress={toggleMuted}
        />
      </View>
    </>
  );
}
