import { useEffect } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { useAuth } from "../lib/auth";
import { list } from "../lib/api";
import { useResource } from "../lib/useResource";
import { Avatar, go, Icon, useUI } from "./ui";

export function StoryTray({ revision }: { revision: number }) {
  const { c, s } = useUI();
  const { user } = useAuth();
  const resource = useResource("/stories");
  const { reload } = resource;
  useEffect(() => { reload(); }, [revision, reload]);
  const groups = list(resource.data);
  const own = groups.find((group) => group.userId === user?.id);
  const others = groups.filter((group) => group.userId !== user?.id);

  if (!user) {
    return null;
  }
  return (
    <View style={{ marginBottom: 24, gap: 12 }}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 20, paddingVertical: 4 }} accessibilityLabel="Stories">
        <View style={{ width: 76, alignItems: "center", gap: 8 }}>
          <Pressable accessibilityRole="button" accessibilityLabel={own ? "View your story" : "Add story"} onPress={() => go(own ? `/stories?user=${user.id}` : "/stories?create=1")}>
            <View style={{ borderWidth: 2, borderColor: own ? c.accent : c.lineStrong, borderRadius: 40, padding: 4 }}>
              <Avatar user={user} size={58} />
            </View>
          </Pressable>
          {own && (
            <Pressable accessibilityRole="button" accessibilityLabel="Add story" onPress={() => go("/stories?create=1")} style={{ position: "absolute", top: 42, right: -4, width: 36, height: 36, borderRadius: 18, backgroundColor: c.accent, alignItems: "center", justifyContent: "center", borderWidth: 3, borderColor: c.bg }}>
              <Icon name="plus" size={18} color={c.onAccent} />
            </Pressable>
          )}
          <Text style={{ color: c.text, fontSize: 11 }}>{own ? "Your story" : "Add story"}</Text>
        </View>
        {others.map((group) => (
          <Pressable key={group.userId} accessibilityRole="button" accessibilityLabel={`View ${group.author.username}'s story${group.hasUnseen ? ", unseen" : ""}`} onPress={() => go(`/stories?user=${group.userId}`)} style={{ width: 76, alignItems: "center", gap: 8 }}>
            <View style={{ borderWidth: 2, borderColor: group.hasUnseen ? c.accent : c.lineStrong, borderRadius: 40, padding: 4 }}>
              <Avatar user={group.author} size={58} />
            </View>
            <Text numberOfLines={1} style={{ color: group.hasUnseen ? c.text : c.dim, fontSize: 11 }}>{group.author.username}</Text>
          </Pressable>
        ))}
        {resource.loading && <View accessibilityLabel="Loading stories" style={[s.skeletonAvatar, { width: 70, height: 70 }]} />}
      </ScrollView>
      {resource.error && (
        <Pressable accessibilityRole="button" onPress={reload} style={{ minHeight: 44, justifyContent: "center" }}>
          <Text style={s.body}>Stories couldn’t load. Tap to retry.</Text>
        </Pressable>
      )}
    </View>
  );
}
