import { useState } from "react";
import { Pressable, Text, View } from "react-native";
import { useResource } from "../lib/useResource";
import { errorMessage, idOf, list, request } from "../lib/api";
import { Avatar, ErrorNote, go, Icon, useUI } from "./ui";

export function DiscoveryAside() {
  const { c, s } = useUI();
  const resource = useResource("/users/suggested");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const people = list(resource.data).slice(0, 4);

  async function follow(person: any) {
    const id = idOf(person);
    if (busy) {
      return;
    }
    setBusy(id);
    setError("");
    try {
      await request(`/users/${id}/follow`, { method: person.isFollowing || person.followRequested ? "DELETE" : "POST" });
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy("");
    }
  }

  return (
    <View style={{ gap: 32 }}>
      <View>
        <Text style={{ color: c.dim, fontSize: 10, letterSpacing: 1.5, marginBottom: 10 }}>EXPAND YOUR PERSPECTIVE</Text>
        <View style={[s.row, { justifyContent: "space-between", marginBottom: 24 }]}>
          <Text style={{ color: c.text, fontSize: 17, fontWeight: "500" }}>Suggested creators</Text>
          <Pressable accessibilityRole="link" accessibilityLabel="See all creators" onPress={() => go("/explore")} style={{ minWidth: 44, minHeight: 44, alignItems: "flex-end", justifyContent: "center" }}>
            <Icon name="arrow-up-right" size={18} />
          </Pressable>
        </View>
        {resource.loading ? (
          <View accessibilityLabel="Loading creators" style={{ gap: 20 }}>
            {[0, 1, 2].map((item) => (
              <View key={item} style={s.row}>
                <View style={s.skeletonAvatar} />
                <View style={[s.skeleton, { flex: 1 }]} />
              </View>
            ))}
          </View>
        ) : resource.error ? (
          <Pressable onPress={resource.reload} accessibilityRole="button" style={{ minHeight: 44 }}>
            <Text style={s.body}>Couldn’t load creators. Tap to retry.</Text>
          </Pressable>
        ) : people.length ? (
          <View style={{ gap: 20 }}>
            {people.map((person: any) => (
              <View key={idOf(person)} style={[s.row, { gap: 10 }]}>
                <Pressable accessibilityRole="link" accessibilityLabel={`View ${person.username}'s profile`} onPress={() => go(`/profile?user=${idOf(person)}`)} style={[s.row, { flex: 1, minWidth: 0, gap: 10 }]}>
                  <Avatar user={person} size={36} />
                  <View style={{ flex: 1, minWidth: 0, gap: 4 }}>
                    <Text numberOfLines={1} style={{ color: c.text, fontSize: 12, fontWeight: "600" }}>{person.username}</Text>
                    <Text numberOfLines={1} style={{ color: c.dim, fontSize: 11 }}>{person.displayName || "A fresh perspective"}</Text>
                  </View>
                </Pressable>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`${person.followRequested ? "Cancel request to" : person.isFollowing ? "Unfollow" : "Follow"} ${person.username}`}
                  disabled={Boolean(busy)}
                  onPress={() => void follow(person)}
                  style={{ minHeight: 44, justifyContent: "center", opacity: busy === idOf(person) ? 0.5 : 1 }}
                >
                  <Text style={{ color: person.isFollowing ? c.dim : c.accentText, fontSize: 11, fontWeight: "600" }}>
                    {person.followRequested ? "Requested" : person.isFollowing ? "Following" : "Follow"}
                  </Text>
                </Pressable>
              </View>
            ))}
          </View>
        ) : (
          <Text style={[s.body, { fontSize: 13 }]}>New perspectives will appear here as the community grows.</Text>
        )}
        <ErrorNote message={error} />
      </View>
    </View>
  );
}
