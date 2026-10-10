import React, { useEffect, useRef, useState } from "react";
import { Platform, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Gate, Shell } from "../components/Shell";
import {
  Button,
  ErrorNote,
  Field,
  go,
  IconButton,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage, list, request } from "../lib/api";
import { useResource } from "../lib/useResource";
import { uploadMedia, UploadJob } from "../lib/upload";

type Block = { type: string; data: Record<string, any> };
const initial = (): Block[] => [{ type: "text", data: { content: "" } }];
export function ComposerScreen() {
  return (
    <Shell>
      <Gate>
        <Composer />
      </Gate>
    </Shell>
  );
}
function Composer() {
  const { c, s, compact } = useUI();
  const { edit, community } = useLocalSearchParams<{
    edit?: string;
    community?: string;
  }>();
  const { user } = useAuth();
  const [blocks, setBlocks] = useState<Block[]>(initial);
  const [visibility, setVisibility] = useState(
    community ? "COMMUNITY_ONLY" : "PUBLIC",
  );
  const [allowedListIds, setAllowedListIds] = useState<string[]>([]);
  const [allowedUserIds, setAllowedUserIds] = useState<string[]>([]);
  const [canManageAudience, setCanManageAudience] = useState(!edit);
  const [communityId, setCommunityId] = useState(community || "");
  const [postType, setPostType] = useState("STANDARD");
  const [tags, setTags] = useState("");
  const [summary, setSummary] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [percent, setPercent] = useState(0);
  const [ready, setReady] = useState(false);
  const [expectedVersion, setExpectedVersion] = useState<number | undefined>();
  const [saved, setSaved] = useState(false);
  const job = useRef<UploadJob | null>(null);
  const communities = useResource("/communities/me");
  const audiences = useResource("/acl/lists");
  const [canResume, setCanResume] = useState(false);
  const draftKey = `dbgram.draft.${user.id}`;
  useEffect(() => {
    let live = true;
    if (edit)
      request(`/posts/${edit}`)
        .then((post) => {
          if (live) {
            setBlocks(post.blocks);
            setExpectedVersion(post.currentVersion);
            setCanManageAudience(post.canManageAudience);
            setAllowedListIds(post.audience?.allowedListIds || []);
            setAllowedUserIds(post.audience?.allowedUserIds || []);
            setVisibility(post.visibility);
            setPostType(post.postType);
            setTags((post.hashtags || []).join(" "));
            setCommunityId(post.communityId || "");
            setReady(true);
          }
        })
        .catch((e) => setError(errorMessage(e)));
    else {
      if (Platform.OS === "web") {
        try {
          const raw = localStorage.getItem(draftKey);
          if (raw) {
            const draft = JSON.parse(raw);
            setBlocks(draft.blocks || initial());
            setVisibility(draft.visibility || "PUBLIC");
            setAllowedListIds(draft.allowedListIds || []);
            setTags(draft.tags || "");
            setPostType(draft.postType || "STANDARD");
            setCommunityId(community || draft.communityId || "");
          }
        } catch {}
      }
      setReady(true);
    }
    return () => {
      live = false;
    };
  }, [edit, draftKey, community]);
  useEffect(() => {
    if (!ready || edit || Platform.OS !== "web") return;
    setSaved(false);
    const timer = setTimeout(() => {
      try {
        localStorage.setItem(
          draftKey,
          JSON.stringify({ blocks, visibility, tags, postType, communityId, allowedListIds }),
        );
        setSaved(true);
      } catch {}
    }, 500);
    return () => clearTimeout(timer);
  }, [blocks, visibility, tags, postType, communityId, allowedListIds, ready, edit, draftKey]);
  function update(index: number, values: any) {
    setBlocks((current) =>
      current.map((block, i) =>
        i === index ? { ...block, data: { ...block.data, ...values } } : block,
      ),
    );
  }
  function add(type: string) {
    setBlocks((b) => [
      ...b,
      {
        type,
        data:
          type === "poll"
            ? { question: "", options: ["", ""] }
            : type === "carousel"
              ? { media_urls: [] }
              : type === "code"
                ? { language: "javascript", content: "" }
                : {},
      },
    ]);
  }
  function move(index: number, direction: number) {
    setBlocks((current) => {
      const next = [...current];
      [next[index], next[index + direction]] = [
        next[index + direction],
        next[index],
      ];
      return next;
    });
  }
  async function upload(file?: File) {
    if (file) { job.current = { file, offset: 0 }; setCanResume(false); }
    if (!job.current) return;
    setUploading(true);
    setError("");
    try {
      const data = await uploadMedia(job.current, setPercent);
      const type = data.mimeType.startsWith("video/")
        ? "video"
        : data.mimeType.startsWith("audio/")
          ? "audio"
          : "image";
      setBlocks((b) => [...b, { type, data }]);
      if (type === "video") setPostType("REEL");
      job.current = null;
      setCanResume(false);
    } catch (e) {
      setError(errorMessage(e));
      setCanResume(!!job.current?.location);
    } finally {
      setUploading(false);
    }
  }
  async function publish() {
    setError("");
    const meaningful = blocks.filter((b) =>
      b.type === "text" || b.type === "code"
        ? b.data.content?.trim()
        : b.type === "poll"
          ? b.data.question?.trim()
          : b.type === "carousel"
            ? b.data.media_urls?.length
            : b.data.url?.trim(),
    );
    if (!meaningful.length) {
      setError("Give your post a little something to say.");
      return;
    }
    if (
      meaningful.some(
        (b) =>
          b.type === "poll" &&
          (b.data.options?.length < 2 ||
            b.data.options.some((o: string) => !o.trim())),
      )
    ) {
      setError("Polls need at least two nonempty choices.");
      return;
    }
    if (visibility === "COMMUNITY_ONLY" && !communityId) {
      setError("Choose the community for this post.");
      return;
    }
    if (visibility === "CUSTOM" && !allowedListIds.length && !allowedUserIds.length) { setError("Choose at least one audience list."); return; }
    setBusy(true);
    try {
      const data = await request(edit ? `/posts/${edit}` : "/posts", {
        method: edit ? "PUT" : "POST",
        body: {
          blocks: meaningful,
          visibility,
          postType,
          hashtags: tags.split(/[\s,#]+/).filter(Boolean),
          communityId: communityId || null,
          editSummary: summary,
          ...(edit ? { expectedVersion } : {}),
          ...(canManageAudience ? { allowedListIds: visibility === "CUSTOM" ? allowedListIds : [], allowedUserIds: visibility === "CUSTOM" ? allowedUserIds : [] } : {}),
        },
      });
      if (Platform.OS === "web" && !edit) localStorage.removeItem(draftKey);
      go(`/post?id=${data.id || edit}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionTitle
        title={edit ? "A fresh take." : "Make something worth sharing."}
        subtitle={
          edit
            ? "Every edit has a story. Your version history stays intact."
            : "A little thought, a big idea, or something in between."
        }
      />
      <View style={[s.card, { padding: compact ? 16 : 28, gap: 24 }]}>
        <View style={[s.row, { justifyContent: "space-between", flexWrap: "wrap" }]}>
          <Text
            style={{
              color: c.accentText,
              fontSize: 10,
              fontWeight: "600",
              letterSpacing: 2,
            }}
          >
            YOUR CANVAS
          </Text>
          <Text style={{ color: c.dim, fontSize: 11 }}>
            {edit
              ? "Editing post"
              : saved
                ? "Draft saved on this device"
                : "Unsaved draft"}
          </Text>
        </View>
        <ErrorNote message={error} />
        {blocks.map((block, index) => (
          <View
            key={index}
            style={{
              borderWidth: 1,
              borderColor: c.line,
              backgroundColor: c.bg,
              borderRadius: 18,
              padding: compact ? 12 : 20,
              gap: 12,
            }}
          >
            <View style={[s.row, { justifyContent: "space-between", flexWrap: "wrap" }]}>
              <Text style={{ color: c.dim, fontSize: 10, letterSpacing: 1.5 }}>
                {String(index + 1).padStart(2, "0")} /{" "}
                {block.type.toUpperCase()}
              </Text>
              <View style={[s.row, { gap: 0 }]}>
                {index > 0 && (
                  <IconButton
                    name="arrow-up"
                    label="Move block up"
                    onPress={() => move(index, -1)}
                  />
                )}
                {index < blocks.length - 1 && (
                  <IconButton
                    name="arrow-down"
                    label="Move block down"
                    onPress={() => move(index, 1)}
                  />
                )}
                <IconButton
                  name="x"
                  label="Remove block"
                  onPress={() =>
                    setBlocks((b) => b.filter((_, i) => i !== index))
                  }
                />
              </View>
            </View>
            {block.type === "text" && (
              <Field
                placeholder="Let the idea out…"
                multiline
                value={block.data.content || ""}
                onChangeText={(content) => update(index, { content })}
                style={{
                  borderWidth: 0,
                  padding: 0,
                  fontSize: 17,
                  lineHeight: 27,
                }}
              />
            )}
            {block.type === "code" && (
              <>
                <Field
                  label="Language"
                  placeholder="javascript"
                  value={block.data.language || ""}
                  onChangeText={(language) => update(index, { language })}
                />
                <Field
                  multiline
                  placeholder="// Show us how it works"
                  value={block.data.content || ""}
                  onChangeText={(content) => update(index, { content })}
                  style={{ fontFamily: "monospace", minHeight: 180 }}
                />
              </>
            )}
            {["image", "video", "audio", "embed"].includes(block.type) && (
              <>
                <Field
                  label={
                    block.data.mediaId ? "Uploaded media" : "Media or link URL"
                  }
                  placeholder="https://…"
                  value={block.data.url || ""}
                  onChangeText={(url) => update(index, { url })}
                  editable={!block.data.mediaId}
                />
                <Field
                  label={
                    block.type === "embed"
                      ? "Link title"
                      : "Caption / alternative text"
                  }
                  value={block.data.caption || ""}
                  onChangeText={(caption) => update(index, { caption })}
                  placeholder="Add a little context"
                />
              </>
            )}
            {block.type === "carousel" && (
              <Field
                multiline
                label="Image URLs, one per line"
                placeholder={"https://…\nhttps://…"}
                value={(block.data.media_urls || []).join("\n")}
                onChangeText={(value) =>
                  update(index, {
                    media_urls: value.split("\n").filter(Boolean),
                  })
                }
              />
            )}
            {block.type === "poll" && (
              <>
                <Field
                  label="Your question"
                  value={block.data.question || ""}
                  placeholder="What do you think?"
                  onChangeText={(question) => update(index, { question })}
                />
                {(block.data.options || []).map((option: string, i: number) => (
                  <Field
                    key={i}
                    value={option}
                    placeholder={`Choice ${i + 1}`}
                    onChangeText={(value) =>
                      update(index, {
                        options: block.data.options.map(
                          (o: string, k: number) => (k === i ? value : o),
                        ),
                      })
                    }
                  />
                ))}
                {block.data.options.length < 6 && (
                  <Button
                    quiet
                    small
                    title="Add a choice"
                    icon="plus"
                    onPress={() =>
                      update(index, { options: [...block.data.options, ""] })
                    }
                  />
                )}
              </>
            )}
          </View>
        ))}
        <View style={{ gap: 12 }}>
          <Text style={s.label}>Build your story</Text>
          <View style={[s.row, { flexWrap: "wrap", gap: 8 }]}>
            {[
              ["text", "type"],
              ["image", "image"],
              ["carousel", "layers"],
              ["code", "code"],
              ["poll", "bar-chart-2"],
              ["embed", "link"],
              ["video", "film"],
            ].map(([type, icon]) => (
              <Button
                key={type}
                title={type.charAt(0).toUpperCase() + type.slice(1)}
                icon={icon}
                small
                quiet
                onPress={() => add(type)}
              />
            ))}
          </View>
          {Platform.OS === "web" && (
            <View
              style={{
                padding: 16,
                borderWidth: 1,
                borderStyle: "dashed",
                borderColor: c.dim,
                borderRadius: 12,
                gap: 12,
              }}
            >
              <Text style={s.label}>Upload an image, video, or audio</Text>
              <input
                type="file"
                accept="image/*,video/*,audio/*"
                disabled={uploading}
                aria-label="Upload media"
                style={{ color: c.muted, maxWidth: "100%", fontSize: 14, minHeight: 44 }}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    void upload(file);
                  }
                }}
              />
              {uploading && (
                <Text style={{ color: c.accentText, fontSize: 12 }}>
                  Uploading {percent}%…
                </Text>
              )}
              {!uploading && canResume && (
                <Button title="Resume upload" small onPress={() => upload()} />
              )}
            </View>
          )}
        </View>
        <Field
          label="Topics"
          value={tags}
          onChangeText={setTags}
          placeholder="#design #photography #buildinpublic"
        />
        <View style={{ gap: 12 }}>
          <Text style={s.label}>Who can see this?</Text>
          <View style={[s.row, { flexWrap: "wrap", gap: 8 }]}>
            {[
              ["PUBLIC", user.isPrivate ? "Account followers" : "Everyone"],
              ["FOLLOWERS_ONLY", "Followers"],
              ["CLOSE_FRIENDS", "Close friends"],
              ["CUSTOM", "Custom audience"],
              ["COMMUNITY_ONLY", "Community"],
              ["PRIVATE", "Only me"],
            ].map(([value, label]) => (
              <Tag
                key={value}
                text={label}
                active={visibility === value}
                onPress={canManageAudience ? () => setVisibility(value) : undefined}
              />
            ))}
          </View>
          {!canManageAudience && <Text style={s.body}>The original author manages this post’s audience.</Text>}
          {user.isPrivate && <Text style={s.body}>Your account is private. Only approved followers can see this, and narrower audiences still apply.</Text>}
          {visibility === "CUSTOM" && canManageAudience && <View style={{ gap: 12 }}>
            <ErrorNote message={audiences.error} />
            <View style={[s.row, { flexWrap: "wrap" }]}>{list(audiences.data).map(circle => <Tag key={circle.id} text={circle.name} active={allowedListIds.includes(circle.id)} onPress={() => setAllowedListIds(current => current.includes(circle.id) ? current.filter(id => id !== circle.id) : [...current, circle.id])} />)}</View>
            {allowedUserIds.length > 0 && <Text style={s.body}>{allowedUserIds.length} individually invited people retain access.</Text>}
            <Button title="Manage your circles" small quiet icon="users" onPress={() => go("/privacy")} />
          </View>}
          {visibility === "COMMUNITY_ONLY" && (
            <View style={[s.row, { flexWrap: "wrap" }]}>
              {list(communities.data).map((group) => (
                <Tag
                  key={group.id}
                  text={group.name}
                  active={communityId === group.id}
                  onPress={() => setCommunityId(group.id)}
                />
              ))}
              {!list(communities.data).length && (
                <Text
                  onPress={() => go("/communities")}
                  style={{ color: c.accentText }}
                >
                  Join or create a community first →
                </Text>
              )}
            </View>
          )}
        </View>
        <View style={[s.row, { flexWrap: "wrap" }]}>
          {[
            ["STANDARD", "Post"],
            ["ARTICLE", "Article"],
            ["REEL", "Reel"],
          ].map(([value, label]) => (
            <Tag
              key={value}
              text={label}
              active={postType === value}
              onPress={() => setPostType(value)}
            />
          ))}
        </View>
        {edit && (
          <Field
            label="What changed?"
            placeholder="A short note for your version history"
            value={summary}
            onChangeText={setSummary}
          />
        )}
        <View
          style={[
            s.row,
            {
              justifyContent: "space-between",
              flexWrap: "wrap",
              borderTopWidth: 1,
              borderColor: c.line,
              paddingTop: 20,
            },
          ]}
        >
          <Button title="Back to feed" quiet onPress={() => go("/")} />
          <Button
            title={edit ? "Save changes" : "Publish your post"}
            icon="arrow-up-right"
            onPress={publish}
            busy={busy}
            disabled={uploading || !ready}
          />
        </View>
      </View>
    </>
  );
}




