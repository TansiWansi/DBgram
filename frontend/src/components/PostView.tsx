import React, { memo, useEffect, useRef, useState } from "react";
import {
  Linking,
  Pressable,
  ScrollView,
  Share,
  Text,
  View,
} from "react-native";
import { useVideoPlayer, VideoView } from "expo-video";
import { useEvent } from "expo";
import { Ionicons } from "@expo/vector-icons";
import { PhotoImage } from "./PhotoImage";
import {
  Avatar,
  Button,
  ErrorNote,
  go,
  Icon,
  IconButton,
  Tag,
  useUI,
} from "./ui";
import { useNow } from "../lib/useNow";
import { queueAction } from "../lib/offline";
import {
  ApiError,
  errorMessage,
  mediaUrl,
  request,
  safeLink,
} from "../lib/api";

function ago(value: string) {
  const minutes = Math.max(
    0,
    Math.floor((Date.now() - new Date(value).getTime()) / 60000),
  );
  return minutes < 1
    ? "Just now"
    : minutes < 60
      ? `${minutes}m`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)}h`
        : new Date(value).toLocaleDateString(undefined, {
          month: "short",
          day: "numeric",
        });
}
export function VideoBlock({ url }: { url: string }) {
  const { c, compact } = useUI();
  const player = useVideoPlayer(mediaUrl(url), (player) => {
    player.loop = true;
  });
  const { error } = useEvent(player, "statusChange", { status: player.status });
  return (
    <View style={{ gap: 8 }}>
      <VideoView
        player={player}
        style={{
          width: "100%",
          height: compact ? 260 : 380,
          backgroundColor: c.ink,
          borderRadius: 4,
        }}
        nativeControls
        fullscreenOptions={{ enable: true }}
        contentFit="contain"
      />
      <ErrorNote
        message={
          error
            ? "This video could not be played. Try reopening the post."
            : undefined
        }
      />
    </View>
  );
}
function Photo({ url }: { url: string }) {
  return <PhotoFrame key={url} url={url} />;
}

function PhotoFrame({ url }: { url: string }) {
  const { c, s } = useUI();
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  return failed ? (
    <View
      style={{
        padding: 40,
        alignItems: "center",
        gap: 10,
        backgroundColor: c.raised,
        aspectRatio: 1,
        justifyContent: "center",
      }}
    >
      <Icon name="image" />
      <Text style={s.body}>Image unavailable</Text>
    </View>
  ) : (
    <View style={{ width: "100%", aspectRatio: 1, backgroundColor: c.raised, overflow: "hidden" }}>
      <PhotoImage uri={mediaUrl(url)} onLoad={() => setLoaded(true)} onError={() => setFailed(true)} />
      {!loaded && (
        <View
          accessibilityRole="progressbar"
          accessibilityLabel="Loading photo"
          pointerEvents="none"
          style={{ position: "absolute", inset: 0, alignItems: "center", justifyContent: "center", backgroundColor: c.raised }}
        >
          <Icon name="image" size={28} color={c.dim} />
        </View>
      )}
    </View>
  );
}
function Carousel({ urls }: { urls: string[] }) {
  const { s } = useUI();
  const [index, setIndex] = useState(0);
  const safeIndex = Math.min(index, Math.max(0, urls.length - 1));
  return (
    <View style={{ gap: 10 }}>
      <Photo url={urls[safeIndex] || ""} />
      <View style={[s.row, { justifyContent: "space-between" }]}>
        <Text style={s.body}>
          {safeIndex + 1} / {urls.length}
        </Text>
        <View style={s.row}>
          <IconButton
            name="arrow-left"
            label="Previous image"
            onPress={() => setIndex((i) => Math.max(0, i - 1))}
          />
          <IconButton
            name="arrow-right"
            label="Next image"
            onPress={() => setIndex((i) => Math.min(urls.length - 1, i + 1))}
          />
        </View>
      </View>
    </View>
  );
}
function Poll({ post, block, index, onChange }: any) {
  const { c, s } = useUI();
  const now = useNow();
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const votes = (post.pollResults || []).filter(
    (r: any) => r.blockIndex === index,
  );
  const total = votes.reduce(
    (sum: number, row: any) => sum + Number(row.votes),
    0,
  );
  const selected = (post.myVotes || []).find(
    (r: any) => r.blockIndex === index,
  )?.optionIndex;
  const ended =
    block.data.expiresAt && Date.parse(block.data.expiresAt) <= now;
  async function vote(optionIndex: number) {
    setBusy(true);
    setError("");
    try {
      onChange?.(
        await request(`/posts/${post.id}/poll/vote`, {
          method: "POST",
          body: { blockIndex: index, optionIndex },
        }),
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <View
      style={{
        gap: 10,
        padding: 18,
        borderRadius: 14,
        backgroundColor: c.bg,
        borderWidth: 1,
        borderColor: c.line,
      }}
    >
      <Text
        style={{
          color: c.text,
          fontWeight: "600",
          fontSize: 17,
          lineHeight: 25,
        }}
      >
        {block.data.question}
      </Text>
      {block.data.options?.map((option: any, i: number) => {
        const amount = Number(
          votes.find((r: any) => r.optionIndex === i)?.votes || 0,
        ),
          percent = total ? Math.round((amount / total) * 100) : 0;
        return (
          <Pressable
            key={i}
            accessibilityRole="button"
            accessibilityLabel={`Vote for ${typeof option === "string" ? option : option.text}`}
            disabled={busy || ended}
            onPress={() => vote(i)}
            style={{
              borderWidth: 1,
              borderColor: selected === i ? c.accent : c.line,
              padding: 13,
              borderRadius: 14,
              minHeight: 48,
              overflow: "hidden",
            }}
          >
            <View
              style={{
                position: "absolute",
                top: 0,
                bottom: 0,
                left: 0,
                width: `${percent}%`,
                backgroundColor: c.accentSoft,
              }}
            />
            <View style={[s.row, { justifyContent: "space-between" }]}>
              <Text style={{ color: c.text, flex: 1 }}>
                {typeof option === "string" ? option : option.text}
              </Text>
              <Text style={{ color: c.accentText, fontSize: 12 }}>
                {selected === i ? "✓  " : ""}
                {percent}%
              </Text>
            </View>
          </Pressable>
        );
      })}
      <Text style={{ color: c.dim, fontSize: 12 }}>
        {total} {total === 1 ? "vote" : "votes"} ·{" "}
        {ended ? "Poll ended" : "One vote per person"}
      </Text>
      <ErrorNote message={error} />
    </View>
  );
}
export const Blocks = memo(function Blocks({
  post,
  onChange,
}: {
  post: any;
  onChange?: (p: any) => void;
}) {
  const { c, s } = useUI();
  return (
    <View style={{ gap: 18 }}>
      {(post.blocks || []).map((block: any, i: number) => {
        const d = block.data || {};
        switch (block.type) {
          case "text":
            return (
              <Text
                key={i}
                selectable
                style={{
                  color: c.text,
                  fontSize: 16,
                  lineHeight: 26,
                  letterSpacing: 0.05,
                }}
              >
                {d.content}
              </Text>
            );
          case "image":
            return <Photo key={i} url={d.url} />;
          case "video":
            return <VideoBlock key={i} url={d.url} />;
          case "audio":
            return (
              <Button
                key={i}
                quiet
                icon="headphones"
                title="Play audio"
                onPress={() => {
                  const url = mediaUrl(d.url);
                  if (url) void Linking.openURL(url);
                }}
              />
            );
          case "carousel":
            return (
              <Carousel key={i} urls={d.media_urls || d.mediaUrls || []} />
            );
          case "code":
            return (
              <View
                key={i}
                style={{
                  backgroundColor: c.input,
                  borderWidth: 1,
                  borderColor: c.line,
                  borderRadius: 4,
                  overflow: "hidden",
                }}
              >
                <View
                  style={[
                    s.row,
                    { padding: 12, borderBottomWidth: 1, borderColor: c.line },
                  ]}
                >
                  <Icon name="code" size={15} color={c.accentText} />
                  <Text style={{ color: c.muted, fontSize: 12 }}>
                    {d.language || "Code"}
                  </Text>
                </View>
                <ScrollView horizontal contentContainerStyle={{ padding: 17 }}>
                  <Text
                    selectable
                    style={{
                      color: c.text,
                      fontFamily: "monospace",
                      fontSize: 13,
                      lineHeight: 22,
                    }}
                  >
                    {d.content}
                  </Text>
                </ScrollView>
              </View>
            );
          case "poll":
            return <Poll key={i} {...{ post, block, index: i, onChange }} />;
          case "embed":
            return (
              <Pressable
                key={i}
                accessibilityRole="link"
                onPress={() => {
                  const url = safeLink(d.url);
                  if (url) void Linking.openURL(url);
                }}
                style={[s.card, { padding: 20, backgroundColor: c.raised }]}
              >
                <View style={[s.row, { justifyContent: "space-between" }]}>
                  <View style={{ gap: 5, flex: 1 }}>
                    <Text style={{ color: c.text, fontWeight: "600" }}>
                      Explore this link
                    </Text>
                    <Text
                      style={{ color: c.muted, fontSize: 13 }}
                      numberOfLines={2}
                    >
                      {d.url}
                    </Text>
                  </View>
                  <Icon name="arrow-up-right" color={c.accentText} />
                </View>
              </Pressable>
            );
          default:
            return (
              <Text key={i} style={s.body}>
                This content needs a newer version of DBgram.
              </Text>
            );
        }
      })}
    </View>
  );
});
export const PostView = memo(function PostView({
  post: initial,
  onChange,
  detail = false,
}: {
  post: any;
  onChange?: (p: any) => void;
  detail?: boolean;
}) {
  const { c, s } = useUI();
  const [post, setPost] = useState(initial);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [menu, setMenu] = useState(false);
  const lastTap = useRef(0);
  useEffect(() => setPost(initial), [initial]);
  function changed(value: any) {
    setPost(value);
    onChange?.(value);
  }
  async function toggle(kind: "like" | "save") {
    if (busy) return;
    setBusy(kind);
    setError("");
    const flag = kind === "like" ? "isLiked" : "isSaved",
      count = `${kind}Count`,
      previous = post;
    setPost({
      ...post,
      [flag]: !post[flag],
      [count]: Math.max(0, Number(post[count] || 0) + (post[flag] ? -1 : 1)),
    });
    try {
      changed(
        await request(`/posts/${post.id}/${kind}`, {
          method: post[flag] ? "DELETE" : "POST",
          body: {},
        }),
      );
    } catch (e) {
      if (e instanceof ApiError && e.status === 0) {
        queueAction(
          post.id,
          kind === "like"
            ? post[flag]
              ? "UNLIKE"
              : "LIKE"
            : post[flag]
              ? "UNSAVE"
              : "SAVE",
        );
        setError("Saved on this device. We’ll sync when you reconnect.");
      } else {
        setPost(previous);
        setError(errorMessage(e));
      }
    } finally {
      setBusy("");
    }
  }
  const author = post.author || post.authors?.[0] || {};
  const authorName = author.username || author.displayName || "Deleted account";
  const blocks = post.blocks || [];
  // Simple photo stories get an Instagram-style caption below the actions.
  // Rich posts retain block order so poll indices and collaborative content stay intact.
  const photoStory = blocks.some((block: any) => ["image", "video", "carousel"].includes(block.type))
    && blocks.every((block: any) => ["text", "image", "video", "carousel"].includes(block.type));
  const mediaBlocks = photoStory ? blocks.filter((block: any) => block.type !== "text") : blocks;
  const captions = photoStory ? blocks.filter((block: any) => block.type === "text") : [];
  async function share() {
    try {
      await Share.share({
        message: `A post by @${author.username} on DBgram`,
        ...(typeof window !== "undefined"
          ? { url: `${window.location.origin}/post?id=${post.id}` }
          : {}),
      });
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <View style={s.card}>
      <View style={[s.row, { padding: 16, alignItems: "center" }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`View ${authorName}'s profile`}
          disabled={!author.id && !author.userId && !author.username}
          onPress={() =>
            go(`/profile?user=${author.id || author.userId || author.username}`)
          }
        >
          <Avatar user={author} size={36} />
        </Pressable>
        <View style={{ flex: 1, minWidth: 0, gap: 4 }}>
          <View style={[s.row, { gap: 6, flexWrap: "wrap" }]}>
            <Text
              style={{ color: c.text, fontSize: 14, fontWeight: "700" }}
              onPress={() =>
                go(
                  `/profile?user=${author.id || author.userId || author.username}`,
                )
              }
            >
              {authorName}
            </Text>
            {author.isVerified && (
              <Icon name="check-circle" size={14} color={c.accentText} />
            )}
            <Text style={{ color: c.dim, fontSize: 12 }}>
              {ago(post.publishedAt || post.createdAt)}
            </Text>
          </View>
          <Text style={{ color: c.muted, fontSize: 12 }}>
            {author.displayName || "Shared on DBgram"}
            {post.authors?.length > 1
              ? ` + ${post.authors.length - 1} collaborators`
              : ""}
            {post.community ? ` / ${post.community.name}` : ""}
          </Text>
        </View>
        <IconButton
          name="more-horizontal"
          label="Post options"
          onPress={() => setMenu(!menu)}
        />
      </View>
      {menu && (
        <View
          style={[
            s.row,
            { paddingHorizontal: 22, paddingBottom: 16, flexWrap: "wrap" },
          ]}
        >
          {post.canEdit && (
            <Button
              small
              quiet
              title="Edit post"
              icon="edit-2"
              onPress={() => go(`/create?edit=${post.id}`)}
            />
          )}
          <Button
            small
            quiet
            title="Version history"
            icon="clock"
            onPress={() => go(`/history?id=${post.id}`)}
          />
          <Button
            small
            quiet
            title="View details"
            onPress={() => go(`/post?id=${post.id}`)}
          />
          <Button
            small
            quiet
            title="Report"
            icon="flag"
            onPress={() => go(`/report?postId=${post.id}`)}
          />
        </View>
      )}
      <Pressable
        onPress={() => {
          const now = Date.now();
          if (now - lastTap.current < 280 && !post.isLiked) void toggle("like");
          lastTap.current = now;
        }}
        style={{ paddingHorizontal: photoStory ? 0 : 20, paddingBottom: photoStory ? 0 : 24 }}
        accessibilityLabel="Post content"
      >
        <Blocks post={{ ...post, blocks: mediaBlocks }} onChange={changed} />
      </Pressable>
      {!photoStory && post.hashtags?.length > 0 && (
        <View
          style={[
            s.row,
            {
              paddingHorizontal: 22,
              paddingBottom: 18,
              flexWrap: "wrap",
              gap: 7,
            },
          ]}
        >
          {post.hashtags.map((tag: string) => (
            <Tag
              key={tag}
              text={`#${tag.replace(/^#/, "")}`}
              onPress={() => go(`/explore?q=${encodeURIComponent(tag)}`)}
            />
          ))}
        </View>
      )}
      <View
        style={[
          s.row,
          {
            paddingHorizontal: 8,
            paddingVertical: 8,
            gap: 4,
            flexWrap: "wrap",
          },
        ]}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={post.isLiked ? "Unlike post" : "Like post"}
          accessibilityState={{ selected: Boolean(post.isLiked), disabled: Boolean(busy) }}
          disabled={!!busy}
          onPress={() => void toggle("like")}
          style={({ pressed }: any) => [s.row, { padding: 8, gap: 7, minHeight: 44, minWidth: 44, transform: [{ scale: pressed ? 0.88 : 1 }] }]}
        >
          <Ionicons
            name={post.isLiked ? "heart" : "heart-outline"}
            color={post.isLiked ? c.accentText : c.text}
            size={24}
          />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="View comments"
          onPress={() => go(`/post?id=${post.id}`)}
          style={[s.row, { padding: 8, gap: 7, minHeight: 44 }]}
        >
          <Icon name="message-circle" size={22} color={c.text} />
        </Pressable>
        <IconButton
          name="send"
          label="Share post"
          onPress={() => void share()}
        />
        <View style={{ flex: 1 }} />
        {post.visibility !== "PUBLIC" && <Icon name="lock" size={13} />}
        <IconButton
          name="bookmark"
          label={post.isSaved ? "Unsave post" : "Save post"}
          active={post.isSaved}
          disabled={Boolean(busy)}
          onPress={() => void toggle("save")}
        />
      </View>
      <View style={{ paddingHorizontal: 20, paddingBottom: 20, gap: 10 }}>
        <Text style={{ color: c.text, fontSize: 13, fontWeight: "600" }}>
          {Number(post.likeCount || 0).toLocaleString()} {Number(post.likeCount) === 1 ? "like" : "likes"}
        </Text>
        {captions.map((block: any, index: number) => (
          <Text key={index} selectable numberOfLines={detail ? undefined : 5} style={{ color: c.text, fontSize: 14, lineHeight: 23 }}>
            {index === 0 && <Text style={{ fontWeight: "600" }}>{authorName} </Text>}
            {block.data?.content}
          </Text>
        ))}
        {photoStory && post.hashtags?.length > 0 && (
          <View style={[s.row, { flexWrap: "wrap", gap: 12 }]}>
            {post.hashtags.map((tag: string) => (
              <Text key={tag} accessibilityRole="link" onPress={() => go(`/explore?q=${encodeURIComponent(tag)}`)} style={{ color: c.accentText, fontSize: 13 }}>
                #{tag.replace(/^#/, "")}
              </Text>
            ))}
          </View>
        )}
        {!detail && (
          <Pressable accessibilityRole="link" onPress={() => go(`/post?id=${post.id}`)} style={{ minHeight: 32, justifyContent: "center" }}>
            <Text style={{ color: c.dim, fontSize: 13 }}>
              {Number(post.commentCount) > 0 ? `View all ${Number(post.commentCount).toLocaleString()} comments` : "Start the conversation"}
            </Text>
          </Pressable>
        )}
      </View>
      {error && (
        <View style={{ paddingHorizontal: 20, paddingBottom: 16 }}>
          <ErrorNote message={error} />
        </View>
      )}
      {detail && Number(post.currentVersion) > 1 && (
        <View style={{ paddingHorizontal: 22, paddingBottom: 12 }}>
          <Text style={{ color: c.dim, fontSize: 12 }}>
            Edited · version {post.currentVersion}
          </Text>
        </View>
      )}
    </View>
  );
});

