import React, { useCallback, useEffect, useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Gate, Shell } from "../components/Shell";
import { FollowRequests } from "../components/AccountPrivacy";
import {
  Avatar,
  Button,
  ErrorNote,
  Field,
  go,
  Icon,
  IconButton,
  ResourceState,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage, idOf, list, request } from "../lib/api";
import { useResource } from "../lib/useResource";

export function NotificationsScreen() {
  const { c, s } = useUI();
  const { user } = useAuth();
  const resource = useResource(user ? "/notifications" : null);
  const invites = useResource(user ? "/collaborations" : null);
  const [error, setError] = useState("");
  async function markRead() {
    try {
      await request("/notifications/read", { method: "PATCH" });
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <Shell>
      <Gate>
        <SectionTitle
          title="A little ripple."
          subtitle="The conversations and connections around your work."
          aside={
            <Button title="Mark all read" quiet small onPress={markRead} />
          }
        />
        <ErrorNote message={error} />
        <FollowRequests onChange={resource.reload} />
        {list(invites.data).map((invite) => (
          <View
            key={invite.id}
            style={[s.card, { padding: 22, gap: 15, marginBottom: 18 }]}
          >
            <View style={s.row}>
              <Icon name="users" color={c.accentText} />
              <Text style={s.label}>You’re invited to collaborate</Text>
            </View>
            <Text style={s.body}>
              {invite.preview || "A creator wants to add your voice to a post."}
            </Text>
            <Text style={s.body}>
              Proposed contribution: {invite.contributionPct}%
            </Text>
            <View style={s.row}>
              {[true, false].map((accept) => (
                <Button
                  key={String(accept)}
                  title={accept ? "Accept invitation" : "Decline"}
                  quiet={!accept}
                  small
                  onPress={async () => {
                    try {
                      await request(`/collaborations/${invite.id}`, {
                        method: "POST",
                        body: { accept },
                      });
                      invites.reload();
                      resource.reload();
                    } catch (e) {
                      setError(errorMessage(e));
                    }
                  }}
                />
              ))}
            </View>
          </View>
        ))}
        <ResourceState
          resource={resource}
          empty={!list(resource.data).length}
          title="All caught up."
          body="When someone connects with your work, you’ll hear about it here."
          icon="bell"
        >
          <View style={[s.card, { padding: 8 }]}>
            {list(resource.data).map((item) => (
              <Pressable
                key={item.id}
                onPress={() =>
                  go(
                    item.postId
                      ? `/post?id=${item.postId}`
                      : `/profile?user=${idOf(item.actor)}`,
                  )
                }
                style={[
                  s.row,
                  {
                    padding: 17,
                    borderBottomWidth: 1,
                    borderColor: c.line,
                    backgroundColor: item.read ? "transparent" : c.accentSoft,
                    borderRadius: 10,
                  },
                ]}
              >
                <Avatar user={item.actor} />
                <View style={{ flex: 1, gap: 5 }}>
                  <Text style={{ color: c.text, fontSize: 14, lineHeight: 21 }}>
                    <Text style={{ fontWeight: "700" }}>
                      {item.actor?.displayName ||
                        item.actor?.username ||
                        "A creator"}
                    </Text>{" "}
                    {(
                      {
                        LIKE: "liked your post.",
                        COMMENT: "joined the conversation on your post.",
                        FOLLOW: "started following you.",
                        FOLLOW_REQUEST: "requested to follow you.",
                        FOLLOW_ACCEPTED: "accepted your follow request.",
                        COLLABORATION: "invited you to collaborate.",
                      } as any
                    )[item.type] || "shared an update with you."}
                  </Text>
                  <Text style={{ color: c.dim, fontSize: 11 }}>
                    {new Date(item.createdAt).toLocaleString()}
                  </Text>
                </View>
                {!item.read && (
                  <View
                    style={{
                      width: 6,
                      height: 6,
                      borderRadius: 5,
                      backgroundColor: c.accent,
                    }}
                  />
                )}
              </Pressable>
            ))}
          </View>
        </ResourceState>
      </Gate>
    </Shell>
  );
}
export function MessagesScreen() {
  const { c, s } = useUI();
  const { user: other } = useLocalSearchParams<{ user?: string }>();
  const { user } = useAuth();
  const conversations = useResource(user ? "/messages/conversations" : null);
  const [query, setQuery] = useState("");
  const people = useResource(
    user && query.length > 1 ? `/search?q=${encodeURIComponent(query)}` : null,
  );
  return (
    <Shell>
      <Gate>
        <SectionTitle
          title={
            other
              ? "A conversation worth having."
              : "Good conversations start here."
          }
          subtitle="A little less broadcasting. A little more connection."
        />
        {other ? (
          <Conversation other={other} onUpdate={conversations.reload} />
        ) : (
          <>
            <View style={{ marginBottom: 24, gap: 12 }}>
              <Field
                label="Start a conversation"
                placeholder="Search for a creator"
                value={query}
                onChangeText={setQuery}
              />
              {(people.data?.users || [])
                .filter((p: any) => idOf(p) !== idOf(user))
                .map((person: any) => (
                  <Pressable
                    key={idOf(person)}
                    onPress={() => go(`/messages?user=${idOf(person)}`)}
                    style={[s.row, { padding: 10 }]}
                  >
                    <Avatar user={person} />
                    <Text style={[s.label, { flex: 1 }]}>
                      {person.displayName} @{person.username}
                    </Text>
                    <Icon name="arrow-right" />
                  </Pressable>
                ))}
            </View>
            <ResourceState
              resource={conversations}
              empty={!list(conversations.data).length}
              title="Say the first hello."
              body="Find a creator above, or open any profile to start a conversation."
              icon="message-circle"
            >
              <View style={[s.card, { padding: 10 }]}>
                {list(conversations.data).map((item) => (
                  <Pressable
                    key={idOf(item.user)}
                    onPress={() => go(`/messages?user=${idOf(item.user)}`)}
                    style={[
                      s.row,
                      {
                        padding: 15,
                        borderBottomWidth: 1,
                        borderColor: c.line,
                      },
                    ]}
                  >
                    <Avatar user={item.user} />
                    <View style={{ flex: 1, minWidth: 0, gap: 5 }}>
                      <Text style={s.label}>
                        {item.user.displayName || item.user.username}
                      </Text>
                      <Text numberOfLines={1} style={s.body}>
                        {item.lastMessage?.content}
                      </Text>
                    </View>
                    {item.unreadCount > 0 && (
                      <Tag active text={String(item.unreadCount)} />
                    )}
                  </Pressable>
                ))}
              </View>
            </ResourceState>
          </>
        )}
      </Gate>
    </Shell>
  );
}
function Conversation({
  other,
  onUpdate,
}: {
  other: string;
  onUpdate: () => void;
}) {
  const { c, s, compact } = useUI();
  const { user } = useAuth();
  const profile = useResource(`/users/${other}`);
  const [messages, setMessages] = useState<any[]>([]);
  const [content, setContent] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [ttl, setTtl] = useState(0);
  const [undo, setUndo] = useState<any>(null);
  const reload = useCallback(async () => {
    try {
      setMessages(
        list(await request(`/messages/${other}`)).sort(
          (a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt),
        ),
      );
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [other]);
  useEffect(() => {
    setMessages([]);
    setLoading(true);
    void reload();
    const timer = setInterval(() => {
      void reload();
    }, 10000);
    return () => clearInterval(timer);
  }, [reload]);
  useEffect(() => {
    if (!undo) return;
    const timer = setTimeout(() => setUndo(null), 19000);
    return () => clearTimeout(timer);
  }, [undo]);
  async function send() {
    if (!content.trim() || busy) return;
    setBusy(true);
    setError("");
    try {
      await request(`/messages/${other}`, {
        method: "POST",
        body: { content, ...(ttl ? { ttlSeconds: ttl } : {}) },
      });
      setContent("");
      await reload();
      onUpdate();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove(id: string) {
    try {
      await request(`/messages/${id}`, { method: "DELETE" });
      setUndo({ id });
      await reload();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <View style={[s.card, { padding: compact ? 16 : 28, gap: 20 }]}>
      <View
        style={[
          s.row,
          { paddingBottom: 17, borderBottomWidth: 1, borderColor: c.line },
        ]}
      >
        <IconButton
          name="arrow-left"
          label="Back to conversations"
          onPress={() => go("/messages")}
        />
        <Avatar user={profile.data} />
        <View style={{ flex: 1 }}>
          <Text style={s.label}>
            {profile.data?.displayName ||
              profile.data?.username ||
              "Conversation"}
          </Text>
          <Text style={{ color: c.dim, fontSize: 11, marginTop: 5 }}>
            Private conversation · refreshes automatically
          </Text>
        </View>
      </View>
      <ErrorNote message={error} />
      <ResourceState
        resource={{ loading, error: "", reload }}
        empty={!messages.length}
        title="A simple hello goes a long way."
        body="Send the first message to start your conversation."
        icon="message-circle"
      >
        <ScrollView
          style={{ maxHeight: compact ? 360 : 480, minHeight: 200 }}
          contentContainerStyle={{ gap: 13, paddingVertical: 5 }}
        >
          {messages.map((message) => (
            <View
              key={message.id}
              style={{
                maxWidth: compact ? "94%" : "86%",
                alignSelf:
                  message.senderId === idOf(user) ? "flex-end" : "flex-start",
                gap: 5,
              }}
            >
              <View
                style={{
                  backgroundColor:
                    message.senderId === idOf(user) ? c.selected : c.raised,
                  borderRadius: 20,
                  padding: 15,
                  gap: 8,
                }}
              >
                <Text
                  selectable
                  style={{ color: c.text, fontSize: 14, lineHeight: 22 }}
                >
                  {message.content}
                </Text>
                <View
                  style={[s.row, { justifyContent: "space-between", gap: 12, flexWrap: "wrap" }]}
                >
                  <Text style={{ color: c.dim, fontSize: 10 }}>
                    {new Date(message.createdAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                    {message.expiresAt ? " · Disappearing" : ""}
                  </Text>
                  {message.senderId === idOf(user) && (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="Delete message"
                      onPress={() => remove(message.id)}
                      style={s.iconButton}
                    >
                      <Icon name="trash-2" size={12} />
                    </Pressable>
                  )}
                </View>
              </View>
            </View>
          ))}
        </ScrollView>
      </ResourceState>
      {undo && (
        <View
          style={[
            s.row,
            { padding: 16, backgroundColor: c.accentSoft, borderRadius: 14, flexWrap: "wrap" },
          ]}
        >
          <Text style={{ color: c.text, flex: 1, fontSize: 12 }}>
            Message deleted. Undo is available for 20 seconds.
          </Text>
          <Button
            title="Undo"
            small
            onPress={async () => {
              try {
                await request(`/messages/${undo.id}/restore`, {
                  method: "POST",
                });
                setUndo(null);
                await reload();
              } catch (e) {
                setError(errorMessage(e));
              }
            }}
          />
        </View>
      )}
      <View style={[s.row, { flexWrap: "wrap", gap: 8 }]}>
        <Icon name="clock" size={14} />
        {[
          [0, "Keep messages"],
          [3600, "1 hour"],
          [86400, "24 hours"],
          [604800, "7 days"],
        ].map(([seconds, label]) => (
          <Tag
            key={seconds}
            text={String(label)}
            active={ttl === seconds}
            onPress={() => setTtl(Number(seconds))}
          />
        ))}
      </View>
      <Field
        placeholder="Write a little hello…"
        value={content}
        onChangeText={setContent}
        multiline
        maxLength={10000}
      />
      <View style={{ alignItems: "flex-end" }}>
        <Button
          title="Send message"
          icon="send"
          disabled={!content.trim()}
          busy={busy}
          onPress={send}
        />
      </View>
    </View>
  );
}
export function Comments({
  post,
  onChange,
}: {
  post: any;
  onChange: () => void;
}) {
  const { c, s } = useUI();
  const { user } = useAuth();
  const resource = useResource(`/posts/${post.id}/comments`);
  const [content, setContent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reply, setReply] = useState<any>(null);
  async function send() {
    if (!content.trim()) return;
    setBusy(true);
    try {
      await request(`/posts/${post.id}/comments`, {
        method: "POST",
        body: { content, parentId: reply?.id || null },
      });
      setContent("");
      setReply(null);
      resource.reload();
      onChange();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <View style={[s.card, { padding: 24, gap: 20 }]}>
      <Text style={{ color: c.text, fontSize: 20, fontWeight: "600" }}>
        Keep the conversation going.
      </Text>
      <ErrorNote message={error} />
      {post.commentsEnabled !== false && (
        <>
          {reply && (
            <Pressable onPress={() => setReply(null)}>
              <Text style={{ color: c.accentText, fontSize: 12 }}>
                Replying to @{reply.author?.username} · Cancel
              </Text>
            </Pressable>
          )}
          <Field
            value={content}
            onChangeText={setContent}
            multiline
            placeholder="Add your perspective…"
            maxLength={3000}
          />
          <Button
            title="Post comment"
            busy={busy}
            disabled={!content.trim()}
            onPress={send}
          />
        </>
      )}
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title="There’s room for your perspective."
        body="Be the first to join the conversation."
        icon="message-circle"
      >
        <View style={{ gap: 18 }}>
          {list(resource.data).map((comment) => (
            <View
              key={comment.id}
              style={[
                s.row,
                {
                  alignItems: "flex-start",
                  marginLeft: comment.parentId ? 20 : 0,
                },
              ]}
            >
              <Avatar user={comment.author} size={32} />
              <View style={{ flex: 1, gap: 6 }}>
                <Text style={s.label}>
                  {comment.author?.displayName || comment.author?.username}
                </Text>
                <Text style={[s.body, { color: c.text }]}>
                  {comment.content}
                </Text>
                <View style={s.row}>
                  <Pressable onPress={() => setReply(comment)}>
                    <Text style={{ color: c.dim, fontSize: 11 }}>Reply</Text>
                  </Pressable>
                  <Text style={{ color: c.dim, fontSize: 11 }}>
                    {new Date(comment.createdAt).toLocaleDateString()}
                  </Text>
                </View>
              </View>
              {(comment.authorId === idOf(user) || post.canEdit) && (
                <IconButton
                  name="trash-2"
                  label="Delete comment"
                  onPress={async () => {
                    try {
                      await request(
                        `/posts/${post.id}/comments/${comment.id}`,
                        { method: "DELETE" },
                      );
                      resource.reload();
                      onChange();
                    } catch (e) {
                      setError(errorMessage(e));
                    }
                  }}
                />
              )}
            </View>
          ))}
        </View>
      </ResourceState>
    </View>
  );
}


