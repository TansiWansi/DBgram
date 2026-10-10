import React, { useEffect, useState } from "react";
import { Image, Pressable, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { LinearGradient } from "expo-linear-gradient";
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
import { PostView } from "../components/PostView";
import { CommunityEvents } from "./StudioScreen";
import { PersonRow } from "./FeedScreen";
import { useAuth } from "../lib/auth";
import { errorMessage, idOf, list, mediaUrl, request } from "../lib/api";
import { useNow } from "../lib/useNow";
import { useResource } from "../lib/useResource";
export function ProfileScreen() {
  const { user: userId } = useLocalSearchParams<{ user?: string }>();
  const { user } = useAuth();
  return (
    <Shell>
      <Gate>{user && <Profile key={userId || idOf(user)} userId={userId || idOf(user)} />}</Gate>
    </Shell>
  );
}
function Profile({ userId }: { userId: string }) {
  const { c, s, compact } = useUI();
  const { user, reloadUser, signOut } = useAuth();
  const resource = useResource(`/users/${userId}`);
  const profile = resource.data;
  const posts = useResource(`/posts/user/${userId}`);
  const [edit, setEdit] = useState(false);
  const [fields, setFields] = useState({
    displayName: "",
    bio: "",
    avatarUrl: "",
    coverUrl: "",
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState("Posts");
  const own = idOf(user) === idOf(profile);
  const followList = useResource(
    profile?.canViewPosts && ["Followers", "Following"].includes(tab)
      ? `/users/${idOf(profile)}/${tab.toLowerCase()}`
      : null,
  );
  useEffect(() => {
    if (profile)
      setFields({
        displayName: profile.displayName || "",
        bio: profile.bio || "",
        avatarUrl: profile.avatarUrl || "",
        coverUrl: profile.coverUrl || "",
      });
  }, [profile]);
  async function action(fn: () => Promise<any>) {
    setBusy(true);
    setError("");
    try {
      await fn();
      resource.reload();
      posts.reload();
      followList.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <ResourceState
      resource={resource}
      empty={!profile}
      title="This creator isn’t here."
      body="Check the profile link and try again."
    >
      {profile && (
        <>
          <View style={[s.card, { marginBottom: 25 }]}>
            {profile.coverUrl ? (
              <Image
                source={{ uri: mediaUrl(profile.coverUrl) }}
                style={{ height: 180, width: "100%" }}
                accessibilityLabel="Profile cover"
              />
            ) : (
              <LinearGradient
                colors={[c.ocean, c.ink, c.moss]}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
                style={{ height: 155, overflow: "hidden" }}
              >
                <View
                  style={{
                    position: "absolute",
                    height: 250,
                    width: 250,
                    borderRadius: 130,
                    right: -15,
                    top: -85,
                    borderWidth: 1,
                    borderColor: "rgba(252, 241, 208, 0.3)",
                  }}
                />
                <View
                  style={{
                    position: "absolute",
                    height: 210,
                    width: 210,
                    borderRadius: 130,
                    right: 5,
                    top: -65,
                    borderWidth: 1,
                    borderColor: "rgba(252, 241, 208, 0.2)",
                  }}
                />
                <Text
                  style={{
                    color: c.clay,
                    position: "absolute",
                    right: 33,
                    top: 30,
                    fontSize: 67,
                  }}
                >
                  ✳
                </Text>
              </LinearGradient>
            )}
            <View style={{ padding: compact ? 18 : 28, paddingTop: 0, gap: 20 }}>
              <View
                style={[
                  s.row,
                  {
                    justifyContent: "space-between",
                    alignItems: "flex-end",
                    flexWrap: "wrap",
                    marginTop: -33,
                  },
                ]}
              >
                <View
                  style={{
                    borderWidth: 5,
                    borderColor: c.panel,
                    borderRadius: 55,
                  }}
                >
                  <Avatar user={profile} size={85} />
                </View>
                {own ? (
                  <Button
                    title="Edit profile"
                    quiet
                    small
                    icon="edit-2"
                    onPress={() => setEdit(!edit)}
                  />
                ) : (
                  <View style={[s.row, { gap: 8, flexWrap: "wrap" }]}>
                    <Button
                      title="Message"
                      small
                      quiet
                      onPress={() => go(`/messages?user=${profile.id}`)}
                    />
                    <Button
                      title={profile.followRequested ? "Requested · cancel" : profile.isFollowing ? "Following" : "Follow"}
                      small
                      busy={busy}
                      icon={profile.isFollowing ? "check" : "plus"}
                      onPress={() =>
                        user
                          ? action(() =>
                              request(`/users/${profile.id}/follow`, {
                                method: profile.isFollowing || profile.followRequested ? "DELETE" : "POST",
                              }),
                            )
                          : go("/login")
                      }
                    />
                  </View>
                )}
              </View>
              <View>
                <Text style={s.title}>
                  {profile.displayName || profile.username}
                </Text>
                <Text style={s.body}>@{profile.username}</Text>
                {profile.isPrivate && <Text style={{ color: c.dim, fontSize: 12, marginTop: 8 }}>Private account</Text>}
              </View>
              {profile.hasStory && (
                <Button title="View story" small quiet icon="play-circle" onPress={() => go(`/stories?user=${profile.id}`)} />
              )}
              {profile.bio && (
                <Text style={[s.body, { color: c.text }]}>{profile.bio}</Text>
              )}
              <View style={[s.row, { gap: 25, flexWrap: "wrap" }]}>
                {[
                  [profile.postCount, "Posts"],
                  [profile.followerCount, "Followers"],
                  [profile.followingCount, "Following"],
                ].map(([count, label]) => (
                  <Pressable key={label} onPress={() => setTab(label)} style={{ minHeight: 44, justifyContent: "center" }}>
                    <Text
                      style={{ color: c.text, fontWeight: "700", fontSize: 18 }}
                    >
                      {count ?? 0}
                      <Text
                        style={{
                          color: c.muted,
                          fontSize: 12,
                          fontWeight: "400",
                        }}
                      >
                        {" "}
                        {label}
                      </Text>
                    </Text>
                  </Pressable>
                ))}
              </View>
              <ErrorNote message={error} />
              {!own && profile.isFollowing && (
                <Button
                  small
                  quiet
                  title={
                    profile.isCloseFriend
                      ? "Remove from close friends"
                      : "Add to close friends"
                  }
                  icon="star"
                  onPress={() =>
                    action(() =>
                      request(`/users/${profile.id}/close-friend`, {
                        method: "PUT",
                        body: { enabled: !profile.isCloseFriend },
                      }),
                    )
                  }
                />
              )}
              {edit && own && (
                <View
                  style={{
                    gap: 15,
                    borderTopWidth: 1,
                    borderColor: c.line,
                    paddingTop: 20,
                  }}
                >
                  <Field
                    label="Display name"
                    value={fields.displayName}
                    onChangeText={(v) =>
                      setFields((f) => ({ ...f, displayName: v }))
                    }
                    maxLength={100}
                  />
                  <Field
                    label="Your story"
                    value={fields.bio}
                    onChangeText={(v) => setFields((f) => ({ ...f, bio: v }))}
                    multiline
                    maxLength={500}
                    placeholder="A few words about you and what you make."
                  />
                  <Field
                    label="Avatar image URL"
                    value={fields.avatarUrl}
                    onChangeText={(v) =>
                      setFields((f) => ({ ...f, avatarUrl: v }))
                    }
                    placeholder="https://…"
                  />
                  <Field
                    label="Cover image URL"
                    value={fields.coverUrl}
                    onChangeText={(v) =>
                      setFields((f) => ({ ...f, coverUrl: v }))
                    }
                    placeholder="https://…"
                  />
                  <Button
                    title="Save profile"
                    busy={busy}
                    onPress={() =>
                      action(async () => {
                        await request("/users/me", {
                          method: "PUT",
                          body: fields,
                        });
                        await reloadUser();
                        setEdit(false);
                      })
                    }
                  />
                </View>
              )}
            </View>
          </View>
          <View style={[s.row, { marginBottom: 23, flexWrap: "wrap" }]}>
            {[
              "Posts",
              "Followers",
              "Following",
              ...(own ? ["Audiences", "Account"] : []),
            ].map((value) => (
              <Tag
                key={value}
                text={value}
                active={tab === value}
                onPress={() => setTab(value)}
              />
            ))}
          </View>
          {!profile.canViewPosts && (
            <View style={[s.card, { padding: 32, alignItems: "center", gap: 16 }]}>
              <Icon name="lock" size={28} color={c.accentText} />
              <Text style={[s.label, { fontSize: 20 }]}>This account is private</Text>
              <Text style={[s.body, { textAlign: "center" }]}>
                {profile.followRequested ? "Your follow request is waiting for approval." : "Follow this account to see their posts, stories, and connections."}
              </Text>
            </View>
          )}
          {profile.canViewPosts && tab === "Posts" && (
            <ResourceState
              resource={posts}
              empty={!list(posts.data).length}
              title={
                own ? "Your story starts here." : "The first chapter is coming."
              }
              body={
                own
                  ? "Share your first post and give your profile a little personality."
                  : "This creator hasn’t shared a post yet."
              }
            >
              <View style={{ gap: 22 }}>
                {list(posts.data).map((post) => (
                  <PostView key={post.id} post={post} onChange={posts.reload} />
                ))}
              </View>
            </ResourceState>
          )}
          {profile.canViewPosts && ["Followers", "Following"].includes(tab) && (
            <ResourceState
              resource={followList}
              empty={!list(followList.data).length}
              title="Good connections take time."
              body="Find a few people who inspire you and say hello."
            >
              <View style={[s.card, { padding: 22 }]}>
                {list(followList.data).map((person) => (
                  <View key={idOf(person)}>
                    <PersonRow person={person} />
                    {own && tab === "Followers" && (
                      <Button title="Remove follower" accessibilityLabel={`Remove ${person.username}`} small quiet disabled={busy} onPress={() => action(() => request(`/users/me/followers/${person.id}`, { method: "DELETE" }))} />
                    )}
                  </View>
                ))}
              </View>
            </ResourceState>
          )}
          {tab === "Audiences" && <AudienceSettings />}
          {tab === "Account" && (
            <View style={[s.card, { padding: 24, gap: 18 }]}>
              <Text style={s.title}>Your space, your rules.</Text>
              <Button title="Account privacy" icon="lock" quiet onPress={() => go("/privacy")} />
              <Text style={s.body}>
                Review your activity, manage your creator tools, or sign out of
                this account.
              </Text>
              <Button
                title="Open creator studio"
                icon="bar-chart-2"
                quiet
                onPress={() => go("/analytics")}
              />
              <Button
                title="Copyright & reports"
                icon="shield"
                quiet
                onPress={() => go("/report")}
              />
              <Button
                title="Sign out"
                icon="log-out"
                quiet
                onPress={() =>
                  action(async () => {
                    await signOut();
                    go("/");
                  })
                }
                busy={busy}
              />
            </View>
          )}
        </>
      )}
    </ResourceState>
  );
}
function AudienceSettings() {
  const { s } = useUI();
  const resource = useResource("/acl/lists");
  const [name, setName] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<any[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const results = useResource(
    query.length > 1 ? `/search?q=${encodeURIComponent(query)}` : null,
  );
  async function save() {
    setBusy(true);
    setError("");
    try {
      await request(editing ? `/acl/lists/${editing.id}` : "/acl/lists", {
        method: editing ? "PUT" : "POST",
        body: { name, userIds: selected.map(idOf) },
      });
      setName("");
      setSelected([]);
      setEditing(null);
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <View style={{ gap: 20 }}>
      <View style={[s.card, { padding: 24, gap: 16 }]}>
        <Text style={s.title}>A smaller circle.</Text>
        <Text style={s.body}>
          Create an audience for posts you want to share with a few specific
          people.
        </Text>
        <ErrorNote message={error} />
        <Field
          label="Audience name"
          placeholder="Design friends, weekend crew…"
          value={name}
          onChangeText={setName}
        />
        <Field
          label="Find people"
          placeholder="Search a name or username"
          value={query}
          onChangeText={setQuery}
        />
        <View style={[s.row, { flexWrap: "wrap" }]}>
          {selected.map((person) => (
            <Tag
              key={idOf(person)}
              text={`@${person.username} ×`}
              active
              onPress={() =>
                setSelected((values) =>
                  values.filter((v) => idOf(v) !== idOf(person)),
                )
              }
            />
          ))}
        </View>
        {(results.data?.users || [])
          .filter(
            (person: any) => !selected.some((p) => idOf(p) === idOf(person)),
          )
          .slice(0, 6)
          .map((person: any) => (
            <Pressable
              key={idOf(person)}
              onPress={() => {
                setSelected((p) => [...p, person]);
                setQuery("");
              }}
              style={s.row}
            >
              <Avatar user={person} size={30} />
              <Text style={s.body}>
                {person.displayName} @{person.username}
              </Text>
              <Icon name="plus" size={16} />
            </Pressable>
          ))}
        <Button
          title={editing ? "Save audience" : "Create audience"}
          disabled={!name.trim()}
          busy={busy}
          onPress={save}
        />
      </View>
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title="Your circles will live here."
        body="Add an audience above to get started."
      >
        {list(resource.data).map((item) => (
          <View
            key={item.id}
            style={[s.card, { padding: 20, marginBottom: 12, gap: 15 }]}
          >
            <Text style={s.label}>{item.name}</Text>
            <Text style={s.body}>
              {(item.userIds || item.members || []).length} people
            </Text>
            <Button
              title="Delete audience"
              small
              quiet
              onPress={async () => {
                try {
                  await request(`/acl/lists/${item.id}`, { method: "DELETE" });
                  resource.reload();
                } catch (e) {
                  setError(errorMessage(e));
                }
              }}
            />
          </View>
        ))}
      </ResourceState>
    </View>
  );
}
export function CommunitiesScreen() {
  const { c, s } = useUI();
  const { user } = useAuth();
  const [mine, setMine] = useState(false);
  const [create, setCreate] = useState(false);
  const resource = useResource(mine ? "/communities/me" : "/communities");
  return (
    <Shell>
      <SectionTitle
        title="Find your kind of people."
        subtitle="Little corners of the internet for the things you love."
        aside={
          <Button
            title="Create"
            icon="plus"
            small
            onPress={() => (user ? setCreate(!create) : go("/login"))}
          />
        }
      />
      <View style={[s.row, { marginBottom: 24, flexWrap: "wrap" }]}>
        <Tag
          text="Discover communities"
          active={!mine}
          onPress={() => setMine(false)}
        />
        <Tag
          text="Your communities"
          active={mine}
          onPress={() => setMine(true)}
        />
      </View>
      {create && (
        <CreateCommunity
          onDone={() => {
            setCreate(false);
            resource.reload();
          }}
        />
      )}
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title={
          mine ? "Your people are out there." : "Start something together."
        }
        body={
          mine
            ? "Join a community that feels like you, or create your own."
            : "Create a space around a shared interest. The best communities start small."
        }
        icon="users"
        action="Create a community"
        onAction={() => (user ? setCreate(true) : go("/login"))}
      >
        <View style={{ gap: 18 }}>
          {list(resource.data).map((group) => (
            <Pressable
              key={group.id}
              onPress={() => go(`/community?id=${group.id}`)}
              style={[s.card, { padding: 24, gap: 16 }]}
            >
              <View style={[s.row, { justifyContent: "space-between" }]}>
                <View
                  style={{
                    width: 45,
                    height: 45,
                    borderRadius: 13,
                    backgroundColor: c.accentSoft,
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Icon
                    name={
                      (group.communityType || group.type) === "TEMPORAL"
                        ? "clock"
                        : "users"
                    }
                    color={c.accentText}
                  />
                </View>
                <Tag
                  text={
                    group.isMember
                      ? "Joined"
                      : (group.communityType || group.type) === "TEMPORAL"
                        ? "Limited time"
                        : "Open community"
                  }
                  active={group.isMember}
                />
              </View>
              <Text
                style={{
                  color: c.text,
                  fontSize: 23,
                  fontWeight: "600",
                  letterSpacing: -0.6,
                }}
              >
                {group.name}
              </Text>
              <Text style={s.body}>
                {group.description ||
                  "A space for shared interests and fresh perspectives."}
              </Text>
              <View style={[s.row, { justifyContent: "space-between" }]}>
                <Text style={{ color: c.dim, fontSize: 12 }}>
                  {group.memberCount || 0} members
                  {group.expiresAt
                    ? ` · Until ${new Date(group.expiresAt).toLocaleDateString()}`
                    : ""}
                </Text>
                <Icon name="arrow-up-right" size={19} color={c.accentText} />
              </View>
            </Pressable>
          ))}
        </View>
      </ResourceState>
    </Shell>
  );
}
function CreateCommunity({ onDone }: { onDone: () => void }) {
  const { s } = useUI();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [temporal, setTemporal] = useState(false);
  const [date, setDate] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function save() {
    setError("");
    if (
      temporal &&
      (!date ||
        !Number.isFinite(Date.parse(date)) ||
        Date.parse(date) < Date.now())
    ) {
      setError("Choose an expiry date in the future (YYYY-MM-DD).");
      return;
    }
    setBusy(true);
    try {
      const value = await request("/communities", {
        method: "POST",
        body: {
          name,
          slug: name
            .toLowerCase()
            .trim()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-|-$/g, ""),
          description,
          type: temporal ? "TEMPORAL" : "PERMANENT",
          visibility: "PUBLIC",
          expiresAt: temporal ? new Date(date).toISOString() : null,
          isPaid: false,
        },
      });
      onDone();
      go(`/community?id=${value.id}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <View style={[s.card, { padding: 24, gap: 16, marginBottom: 24 }]}>
      <Text style={s.title}>Room for your people.</Text>
      <ErrorNote message={error} />
      <Field
        label="Community name"
        value={name}
        onChangeText={setName}
        placeholder="Give your space a name"
        maxLength={100}
      />
      <Field
        label="What brings you together?"
        value={description}
        onChangeText={setDescription}
        multiline
        placeholder="A little about your community…"
      />
      <View style={s.row}>
        <Tag
          text="Always open"
          active={!temporal}
          onPress={() => setTemporal(false)}
        />
        <Tag
          text="Limited-time event"
          active={temporal}
          onPress={() => setTemporal(true)}
        />
      </View>
      {temporal && (
        <Field
          label="Closes on (YYYY-MM-DD)"
          placeholder="2026-12-31"
          value={date}
          onChangeText={setDate}
        />
      )}
      <Button
        title="Create community"
        disabled={!name.trim()}
        busy={busy}
        onPress={save}
      />
    </View>
  );
}
export function CommunityScreen() {
  const { c, s } = useUI();
  const now = useNow();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { user } = useAuth();
  const resource = useResource(id ? `/communities/${id}` : null);
  const posts = useResource(id ? `/feed?communityId=${id}` : null);
  const group = resource.data;
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const expired = group?.expiresAt && Date.parse(group.expiresAt) <= now;
  async function join() {
    if (!user) return go("/login");
    setBusy(true);
    try {
      await request(`/communities/${id}/${group.isMember ? "leave" : "join"}`, {
        method: group.isMember ? "DELETE" : "POST",
      });
      resource.reload();
      posts.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Shell>
      <ResourceState
        resource={resource}
        empty={!group}
        title="This space isn’t available."
        body="The community may have ended or moved on."
      >
        {group && (
          <>
            <LinearGradient
              colors={[c.selected, c.panel]}
              style={[s.card, { padding: 28, gap: 18, marginBottom: 26 }]}
            >
              <Icon name="users" size={32} color={c.accentText} />
              <Text style={s.title}>{group.name}</Text>
              <Text style={s.body}>{group.description}</Text>
              <Text style={{ color: c.accentText, fontSize: 12 }}>
                {group.memberCount || 0} members
                {group.expiresAt
                  ? ` · ${expired ? "Closed" : "Closes"} ${new Date(group.expiresAt).toLocaleString()}`
                  : ""}
              </Text>
              <ErrorNote message={error} />
              <View style={[s.row, { flexWrap: "wrap" }]}>
                <Button
                  title={
                    expired
                      ? "This event has ended"
                      : group.ownerId === idOf(user)
                        ? "Your community"
                        : group.isMember
                          ? "Leave community"
                          : "Join the conversation"
                  }
                  quiet={group.isMember}
                  busy={busy}
                  disabled={expired || group.ownerId === idOf(user)}
                  onPress={join}
                />
                {group.isMember && !expired && (
                  <Button
                    title="Share with community"
                    icon="plus"
                    onPress={() => go(`/create?community=${id}`)}
                  />
                )}
              </View>
            </LinearGradient>
            <CommunityEvents group={group} />
            <SectionTitle title="Around the community" />
            <ResourceState
              resource={posts}
              empty={!list(posts.data).length}
              title="Break the ice."
              body="A simple hello can be the start of something great."
            >
              <View style={{ gap: 22 }}>
                {list(posts.data).map((post) => (
                  <PostView key={post.id} post={post} onChange={posts.reload} />
                ))}
              </View>
            </ResourceState>
          </>
        )}
      </ResourceState>
    </Shell>
  );
}


