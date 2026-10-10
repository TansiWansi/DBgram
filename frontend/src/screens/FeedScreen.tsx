import React, { useEffect, useRef, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Comments } from "./ConversationsScreen";
import { Gate, Shell } from "../components/Shell";
import { DiscoveryAside } from "../components/DiscoveryAside";
import { StoryTray } from "../components/StoryTray";
import {
  Avatar,
  Button,
  Field,
  go,
  Icon,
  IconButton,
  ResourceState,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { PostView } from "../components/PostView";
import { useAuth } from "../lib/auth";
import { errorMessage, idOf, list, request } from "../lib/api";
import { useResource } from "../lib/useResource";

export function FeedScreen({ saved = false, reels = false }: { saved?: boolean; reels?: boolean }) {
  const { c, s } = useUI();
  const { user } = useAuth();
  const [mode, setMode] = useState("ranked");
  const [posts, setPosts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cursor, setCursor] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const generation = useRef(0);
  const endpoint = `/feed/page?mode=${saved ? "saved" : mode}${reels ? "&postType=REEL" : ""}&size=20`;

  useEffect(() => {
    let live = true;
    generation.current += 1;
    setPosts([]);
    setCursor(null);
    setBusy(false);
    setError("");
    if (!user?.id) {
      setLoading(false);
      return;
    }
    setLoading(true);
    request(endpoint)
      .then((data) => {
        if (live) {
          setPosts(list(data));
          setCursor(data.nextCursor || null);
        }
      })
      .catch((e) => {
        if (live) {
          setError(errorMessage(e));
        }
      })
      .finally(() => {
        if (live) {
          setLoading(false);
        }
      });
    return () => {
      live = false;
    };
  }, [endpoint, revision, user?.id]);

  async function loadMore() {
    if (!cursor || busy) {
      return;
    }
    const current = generation.current;
    setBusy(true);
    setError("");
    try {
      const data = await request(`${endpoint}&cursor=${encodeURIComponent(cursor)}`);
      // A response from a previous feed or account cannot append to the current page.
      if (current === generation.current) {
        const values = list(data);
        setPosts((previous) => [
          ...previous,
          ...values.filter((item) => !previous.some((existing) => existing.id === item.id)),
        ]);
        setCursor(data.nextCursor || null);
      }
    } catch (e) {
      if (current === generation.current) {
        setError(errorMessage(e));
      }
    } finally {
      if (current === generation.current) {
        setBusy(false);
      }
    }
  }

  const reload = () => setRevision((value) => value + 1);
  const content = (
    <>
      {saved || reels ? (
        <SectionTitle
          title={saved ? "Your collection" : "In motion"}
          subtitle={saved ? "The moments you want to come back to." : "Films from your community."}
          aside={<IconButton name="refresh-cw" label="Refresh feed" onPress={reload} />}
        />
      ) : <StoryTray revision={revision} />}
      {!saved && !reels && (
        <>
          <View accessibilityRole="tablist" style={[s.row, { gap: 24, marginBottom: 24, borderBottomWidth: 1, borderColor: c.line }]}>
            {[["ranked", "For you"], ["discover", "Latest"], ["following", "Following"]].map(([value, label]) => (
              <Pressable
                key={value}
                accessibilityRole="tab"
                accessibilityState={{ selected: mode === value }}
                onPress={() => setMode(value)}
                style={{
                  minHeight: 44,
                  paddingHorizontal: 0,
                  justifyContent: "center",
                  borderBottomWidth: 2,
                  borderColor: mode === value ? c.accent : "transparent",
                }}
              >
                <Text style={{ color: mode === value ? c.text : c.dim, fontSize: 13, fontWeight: mode === value ? "600" : "400" }}>{label}</Text>
              </Pressable>
            ))}
            <View style={{ flex: 1, alignItems: "flex-end" }}>
              <IconButton name="refresh-cw" label="Refresh feed" onPress={reload} />
            </View>
          </View>
        </>
      )}
      <Gate>
        <ResourceState
          resource={{ loading, error, reload }}
          empty={!posts.length}
          icon={saved ? "bookmark" : reels ? "film" : "feather"}
          title={saved ? "Keep the good stuff close." : reels ? "The stage is yours." : "Every community starts with a hello."}
          body={saved ? "Tap the bookmark on any post to keep it here for later." : reels ? "Share your first video and give the community a new perspective." : mode === "following" ? "Follow a few creators and their latest posts will appear here." : "There are no posts here yet. Be the first to put an idea into the world."}
          action={saved ? "Explore posts" : mode === "following" ? "Find creators" : undefined}
          onAction={saved || mode === "following" ? () => go(saved ? "/" : "/explore") : undefined}
        >
          <View style={{ gap: 32 }}>
            {posts.map((post) => <PostView key={post.id} post={post} onChange={reload} />)}
          </View>
          {cursor && (
            <View style={{ alignItems: "center", marginTop: 25 }}>
              <Button title="Load more moments" quiet busy={busy} onPress={loadMore} />
            </View>
          )}
        </ResourceState>
      </Gate>
    </>
  );

  return (
    <Shell aside={!saved && !reels ? <DiscoveryAside /> : undefined}>
      {content}
    </Shell>
  );
}

export function PersonRow({ person }: { person: any }) {
  const { c, s } = useUI();
  return (
    <Pressable
      accessibilityRole="link"
      onPress={() => go(`/profile?user=${idOf(person)}`)}
      style={[s.row, { paddingVertical: 16, borderBottomWidth: 1, borderColor: c.line }]}
    >
      <Avatar user={person} />
      <View style={{ flex: 1, minWidth: 0, gap: 3 }}>
        <Text style={s.label}>{person.displayName || person.username}</Text>
        <Text numberOfLines={2} style={[s.body, { fontSize: 13 }]}>
          @{person.username}{person.bio ? ` · ${person.bio}` : ""}
        </Text>
      </View>
      <Icon name="arrow-up-right" size={18} />
    </Pressable>
  );
}

export function ExploreScreen() {
  const { c, s, compact } = useUI();
  const { user } = useAuth();
  const params = useLocalSearchParams<{ q?: string }>();
  const [query, setQuery] = useState(params.q || "");
  const [search, setSearch] = useState(query);
  const [tab, setTab] = useState("All");

  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 350);
    return () => clearTimeout(timer);
  }, [query]);

  const resource = useResource(user ? (search ? `/search?q=${encodeURIComponent(search)}` : "/users/suggested") : null);
  const data: any = resource.data;
  const users = search ? data?.users || [] : list(data);
  const posts = data?.posts || [];
  const communities = data?.communities || [];

  return (
    <Shell>
      <Gate>
        <SectionTitle title="Follow your curiosity." subtitle="Find the people, ideas, and communities that feel like you." />
        <View style={[s.row, s.card, { paddingHorizontal: 16, borderRadius: 18, marginBottom: 20 }]}>
          <Icon name="search" color={c.accentText} />
          <Field
            placeholder={compact ? "People, ideas, #tags…" : "Search creators, ideas, or #tags"}
            value={query}
            onChangeText={setQuery}
            containerStyle={{ flex: 1 }}
            style={{ borderWidth: 0, backgroundColor: "transparent", paddingHorizontal: 0, minHeight: 58 }}
          />
        </View>
        <View style={[s.row, { marginBottom: 28, flexWrap: "wrap", gap: 8 }]}>
          {["All", "People", "Posts", "Communities"].map((value) => (
            <Tag key={value} text={value} active={tab === value} onPress={() => setTab(value)} />
          ))}
        </View>
        <ResourceState
          resource={resource}
          empty={!users.length && !posts.length && !communities.length}
          title={search ? "Still looking for the spark?" : "New connections start here."}
          body={search ? "Try a different name, topic, or hashtag." : "Creators will appear here as your community grows."}
          icon="compass"
        >
          <View style={{ gap: 24 }}>
            {(tab === "All" || tab === "People") && users.length > 0 && (
              <View style={[s.card, { padding: compact ? 18 : 24 }]}>
                <Text style={s.eyebrow}>{search ? "PEOPLE" : "PEOPLE TO DISCOVER"}</Text>
                {users.map((person: any) => <PersonRow key={idOf(person)} person={person} />)}
              </View>
            )}
            {(tab === "All" || tab === "Posts") && posts.map((post: any) => (
              <PostView key={post.id} post={post} onChange={resource.reload} />
            ))}
            {(tab === "All" || tab === "Communities") && communities.map((community: any) => (
              <Pressable
                key={community.id}
                accessibilityRole="link"
                onPress={() => go(`/community?id=${community.id}`)}
                style={[s.card, { padding: 24, gap: 10 }]}
              >
                <Icon name="users" color={c.accentText} />
                <Text style={s.label}>{community.name}</Text>
                <Text style={s.body}>{community.description}</Text>
              </Pressable>
            ))}
          </View>
        </ResourceState>
      </Gate>
    </Shell>
  );
}

export function PostScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { user } = useAuth();
  const resource = useResource(id && user ? `/posts/${id}` : null);
  return (
    <Shell>
      <Gate>
        <SectionTitle
          title="A closer look."
          subtitle="Ideas are better when they become conversations."
          aside={<Button small quiet title="History" icon="clock" onPress={() => go(`/history?id=${id}`)} />}
        />
        <ResourceState resource={resource} empty={!resource.data} title="Post not found" body="This post may no longer be available.">
          {resource.data && (
            <>
              <PostView post={resource.data} onChange={resource.reload} detail />
              <Comments post={resource.data} onChange={resource.reload} />
            </>
          )}
        </ResourceState>
      </Gate>
    </Shell>
  );
}
