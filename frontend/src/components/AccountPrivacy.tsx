import { useState } from "react";
import { Pressable, Text, View } from "react-native";
import { useAuth } from "../lib/auth";
import { errorMessage, list, request } from "../lib/api";
import { useResource } from "../lib/useResource";
import { Avatar, Button, ErrorNote, go, Icon, useUI } from "./ui";

export function AccountPrivacy() {
  const { c, s } = useUI();
  const { user, reloadUser } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function change(isPrivate: boolean) {
    if (busy || isPrivate === Boolean(user.isPrivate)) {
      return;
    }
    setBusy(true);
    setError("");
    try {
      await request("/users/me/privacy", { method: "PUT", body: { isPrivate } });
      await reloadUser();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={[s.card, { padding: 24, gap: 20, marginBottom: 28 }]}>
      <View style={s.row}>
        <Icon name={user.isPrivate ? "lock" : "globe"} color={c.accentText} />
        <Text style={[s.label, { flex: 1, fontSize: 20 }]}>Account privacy</Text>
      </View>
      <View accessibilityRole="radiogroup" accessibilityLabel="Account visibility" style={[s.row, { gap: 12 }]}>
        {[[false, "Public"], [true, "Private"]].map(([value, label]) => (
          <Pressable
            key={String(label)}
            accessibilityRole="radio"
            accessibilityLabel={`${label} account`}
            aria-checked={Boolean(user.isPrivate) === value}
            accessibilityState={{ checked: Boolean(user.isPrivate) === value, disabled: busy }}
            disabled={busy}
            onPress={() => void change(Boolean(value))}
            style={{ flex: 1, minHeight: 52, justifyContent: "center", alignItems: "center", borderRadius: 4, borderWidth: 1, borderColor: Boolean(user.isPrivate) === value ? c.accentText : c.line, backgroundColor: Boolean(user.isPrivate) === value ? c.accentSoft : c.bg, opacity: busy ? 0.6 : 1 }}
          >
            <Text style={{ color: c.text, fontWeight: "600" }}>{String(label)}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={s.body}>
        {user.isPrivate
          ? "Only approved followers can see your posts and stories. New followers need your approval."
          : "Anyone signed in to DBgram can see posts and stories shared with your account audience."}
      </Text>
      <Text style={{ color: c.dim, fontSize: 13, lineHeight: 21 }}>
        Your name, photo, bio, and counts stay visible. Changes apply to existing content.
        Existing followers stay approved; pending requests still need your decision.
        Close friends, custom audiences, and Only me remain restricted in either mode.
      </Text>
      <ErrorNote message={error} />
      {busy && <Text accessibilityLiveRegion="polite" style={s.body}>Updating privacy…</Text>}
    </View>
  );
}

export function FollowRequests({ onChange }: { onChange?: () => void }) {
  const { c, s } = useUI();
  const { reloadUser } = useAuth();
  const resource = useResource("/users/me/follow-requests");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const people = list(resource.data);

  async function respond(id: string, accept: boolean) {
    if (busy) {
      return;
    }
    setBusy(id);
    setError("");
    try {
      await request(`/users/me/follow-requests/${id}${accept ? "/accept" : ""}`, {
        method: accept ? "POST" : "DELETE",
      });
      resource.reload();
      await reloadUser();
      onChange?.();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy("");
    }
  }

  if (!people.length && !resource.error && !error) {
    return null;
  }
  return (
    <View style={[s.card, { padding: 20, gap: 18, marginBottom: 28 }]}>
      <Text style={[s.label, { fontSize: 18 }]}>Follow requests {people.length ? `· ${people.length}` : ""}</Text>
      <ErrorNote message={error || resource.error} />
      {resource.error && <Button title="Retry requests" small quiet onPress={resource.reload} />}
      {people.map((person) => (
        <View key={person.id} style={{ gap: 12, paddingBottom: 16, borderBottomWidth: 1, borderColor: c.line }}>
          <Pressable accessibilityRole="link" accessibilityLabel={`View ${person.username}`} onPress={() => go(`/profile?user=${person.id}`)} style={s.row}>
            <Avatar user={person} size={40} />
            <View style={{ flex: 1 }}>
              <Text style={s.label}>{person.displayName || person.username}</Text>
              <Text style={s.body}>@{person.username}</Text>
            </View>
          </Pressable>
          <View style={[s.row, { flexWrap: "wrap" }]}>
            <Button title="Accept" accessibilityLabel={`Accept ${person.username}`} small busy={busy === person.id} disabled={Boolean(busy)} onPress={() => void respond(person.id, true)} />
            <Button title="Decline" small quiet disabled={Boolean(busy)} onPress={() => void respond(person.id, false)} />
          </View>
        </View>
      ))}
    </View>
  );
}
