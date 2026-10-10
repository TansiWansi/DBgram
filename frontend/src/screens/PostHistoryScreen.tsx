import React, { useEffect, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Gate, Shell } from "../components/Shell";
import {
  Avatar,
  Button,
  ErrorNote,
  Field,
  go,
  ResourceState,
  SectionTitle,
  useUI,
  Tag,
} from "../components/ui";
import { useResource } from "../lib/useResource";
import { errorMessage, list, request } from "../lib/api";
import { useAuth } from "../lib/auth";
function textual(blocks: any[]) {
  return (blocks || [])
    .map(
      (b) =>
        b.data?.content || b.data?.question || b.data?.url || `[${b.type}]`,
    )
    .join("\n\n");
}
export default function PostHistoryScreen() {
  return (
    <Shell>
      <Gate>
        <History />
      </Gate>
    </Shell>
  );
}
function History() {
  const { c, s } = useUI();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { user } = useAuth();
  const post = useResource(id ? `/posts/${id}` : null),
    history = useResource(id ? `/posts/${id}/history` : null);
  const [selected, setSelected] = useState<any>(null),
    [confirm, setConfirm] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [percent, setPercent] = useState("25"),
    [message, setMessage] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query), 300);
    return () => clearTimeout(timer);
  }, [query]);
  const people = useResource(
    search.length > 1 ? `/search?q=${encodeURIComponent(search)}` : null,
  );
  const original = post.data?.authors?.some(
    (a: any) => a.id === user.id && a.role === "AUTHOR",
  );
  async function revert() {
    setBusy(true);
    setError("");
    try {
      await request(`/posts/${id}/revert/${selected.versionNumber}`, {
        method: "POST",
        body: {},
      });
      post.reload();
      history.reload();
      setConfirm(false);
      setSelected(null);
      setMessage("Version restored. Your previous content remains in history.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function invite(person: any) {
    setBusy(true);
    setError("");
    try {
      await request(`/posts/${id}/authors/invite`, {
        method: "POST",
        body: { userId: person.id, contributionPct: Number(percent) },
      });
      setMessage(
        `Invitation sent to @${person.username}. They must accept before becoming an author.`,
      );
      setQuery("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <SectionTitle
        title="Every idea has a history."
        subtitle="See what changed, restore a version, or create together."
        aside={
          <Button
            small
            quiet
            title="Back to post"
            onPress={() => go(`/post?id=${id}`)}
          />
        }
      />
      <ErrorNote message={error} />
      {message && (
        <Text
          accessibilityRole="alert"
          style={{ color: c.accentText, paddingVertical: 16 }}
        >
          {message}
        </Text>
      )}
      <ResourceState
        resource={post}
        empty={!post.data}
        title="Post unavailable"
        body="This post is no longer available to your account."
      >
        {post.data && (
          <View style={{ gap: 22 }}>
            <View style={[s.card, { padding: 24, gap: 15 }]}>
              <Tag
                text={`Current version ${post.data.currentVersion}`}
                active
              />
              <Text style={s.body}>{textual(post.data.blocks)}</Text>
            </View>
            <ResourceState
              resource={history}
              empty={!list(history.data).length}
              title="The first draft is still here."
              body="Every saved edit creates a version you can revisit."
            >
              {list(history.data).map((version) => (
                <Pressable
                  key={version.id}
                  onPress={() => {
                    setSelected(version);
                    setConfirm(false);
                  }}
                  style={[
                    s.card,
                    {
                      padding: 20,
                      marginBottom: 12,
                      borderColor:
                        selected?.id === version.id ? c.accent : c.line,
                    },
                  ]}
                >
                  <Text style={s.label}>Version {version.versionNumber}</Text>
                  <Text style={s.body}>
                    {version.editSummary || "Content updated"} ·{" "}
                    {new Date(version.createdAt).toLocaleString()}
                  </Text>
                </Pressable>
              ))}
            </ResourceState>
            {selected && (
              <View style={[s.card, { padding: 24, gap: 20 }]}>
                <Text style={s.title}>Version {selected.versionNumber}</Text>
                <Text style={s.body}>Previous content</Text>
                <Text
                  selectable
                  style={{
                    color: c.danger,
                    lineHeight: 24,
                    backgroundColor: c.dangerSoft,
                    padding: 15,
                    borderRadius: 10,
                  }}
                >
                  {textual(selected.blocks)}
                </Text>
                <Text style={s.body}>Current content</Text>
                <Text
                  selectable
                  style={{
                    color: c.text,
                    lineHeight: 24,
                    backgroundColor: c.selected,
                    padding: 15,
                    borderRadius: 10,
                  }}
                >
                  {textual(post.data.blocks)}
                </Text>
                {post.data.canEdit &&
                  (confirm ? (
                    <View style={{ gap: 14 }}>
                      <Text style={s.body}>
                        Restore version {selected.versionNumber} as a new
                        version? The current version will be saved in history.
                      </Text>
                      <View style={[s.row, { flexWrap: "wrap" }]}>
                        <Button
                          title="Restore this version"
                          busy={busy}
                          onPress={revert}
                        />
                        <Button
                          title="Cancel"
                          quiet
                          onPress={() => setConfirm(false)}
                        />
                      </View>
                    </View>
                  ) : (
                    <Button
                      title="Restore this version"
                      quiet
                      onPress={() => setConfirm(true)}
                    />
                  ))}
              </View>
            )}
            <View style={[s.card, { padding: 24, gap: 18 }]}>
              <Text style={s.title}>Made together.</Text>
              {post.data.authors?.map((a: any) => (
                <View key={a.id} style={s.row}>
                  <Avatar user={a} />
                  <View style={{ flex: 1 }}>
                    <Text style={s.label}>{a.displayName}</Text>
                    <Text style={s.body}>
                      @{a.username} · {a.role.toLowerCase().replace("_", " ")}
                    </Text>
                  </View>
                  <Tag text={`${a.contributionPct}%`} />
                </View>
              ))}
              {original && (
                <>
                  <Text style={s.body}>
                    Invite a coauthor. Their contribution comes from your share
                    after they accept.
                  </Text>
                  <Field
                    label="Find a collaborator"
                    value={query}
                    onChangeText={setQuery}
                    placeholder="Name or username"
                  />
                  <Field
                    label="Contribution percentage (1–99)"
                    value={percent}
                    onChangeText={setPercent}
                    keyboardType="number-pad"
                  />
                  {(people.data?.users || [])
                    .filter(
                      (p: any) =>
                        !post.data.authors.some((a: any) => a.id === p.id),
                    )
                    .slice(0, 5)
                    .map((p: any) => (
                      <View key={p.id} style={s.row}>
                        <Avatar user={p} />
                        <Text style={[s.body, { flex: 1 }]}>@{p.username}</Text>
                        <Button
                          small
                          quiet
                          title="Invite"
                          busy={busy}
                          onPress={() => invite(p)}
                        />
                      </View>
                    ))}
                </>
              )}
            </View>
          </View>
        )}
      </ResourceState>
    </>
  );
}
