import React, { useEffect, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { Gate, Shell } from "../components/Shell";
import { AccountPrivacy, FollowRequests } from "../components/AccountPrivacy";
import {
  Avatar,
  Button,
  ErrorNote,
  Field,
  Icon,
  ResourceState,
  SectionTitle,
  Tag,
  useUI,
} from "../components/ui";
import { errorMessage, list, request } from "../lib/api";
import { useResource } from "../lib/useResource";

export default function PrivacyScreen() {
  return (
    <Shell>
      <Gate>
        <AccountPrivacy />
        <FollowRequests />
        <AudienceLists />
      </Gate>
    </Shell>
  );
}

function AudienceLists() {
  const { c, s, compact } = useUI();
  const resource = useResource("/acl/lists");
  const [editing, setEditing] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [members, setMembers] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);

  const people = useResource(search ? `/search?q=${encodeURIComponent(search)}` : "/users/suggested");
  const results = search ? people.data?.users || [] : list(people.data);
  const profiles = new Map<string, any>(
    [...list(resource.data).flatMap((circle) => circle.members || []), ...results]
      .map((person) => [person.id, person]),
  );
  const reset = () => {
    setEditing(null);
    setName("");
    setMembers([]);
    setQuery("");
    setRemoving(null);
  };

  async function save() {
    if (!name.trim()) {
      setError("Give your circle a name.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await request(editing ? `/acl/lists/${editing}` : "/acl/lists", {
        method: editing ? "PUT" : "POST",
        body: { name: name.trim(), userIds: members },
      });
      reset();
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    setBusy(true);
    setError("");
    try {
      await request(`/acl/lists/${id}`, { method: "DELETE" });
      if (editing === id) {
        reset();
      }
      setRemoving(null);
      resource.reload();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <SectionTitle
        title="Your people. Your circle."
        subtitle="Choose who sees the things you share. Audience changes apply to your existing posts immediately."
      />
      <ErrorNote message={error} />
      <View style={[s.card, { padding: compact ? 18 : 28, gap: 20, marginBottom: 24 }]}>
        <View style={s.row}>
          <Icon name="shield" color={c.accentText} />
          <Text style={[s.label, { flex: 1 }]}>{editing ? "Edit your circle" : "Create an audience list"}</Text>
        </View>
        <Field label="Circle name" placeholder="Weekend crew, good friends…" value={name} onChangeText={setName} maxLength={100} />
        <Text style={s.body}>{members.length} of 100 people selected. Tap a person to add or remove them.</Text>
        {members.length > 0 && (
          <View style={[s.row, { flexWrap: "wrap", gap: 8 }]}>
            {members.map((id) => (
              <Tag
                key={id}
                text={`${profiles.get(id)?.displayName || profiles.get(id)?.username || "Unavailable account"} ×`}
                active
                onPress={() => setMembers((current) => current.filter((value) => value !== id))}
              />
            ))}
          </View>
        )}
        <Field label="Find people" placeholder="Search a name or username" value={query} onChangeText={setQuery} />
        <ErrorNote message={people.error} />
        {results.map((person: any) => {
          const selected = members.includes(person.id);
          return (
            <Pressable
              key={person.id}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: selected }}
              onPress={() => setMembers((current) => selected
                ? current.filter((id) => id !== person.id)
                : current.length < 100 ? [...current, person.id] : current)}
              style={[s.row, { paddingVertical: 12, borderBottomWidth: 1, borderColor: c.line }]}
            >
              <Avatar user={person} size={40} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={s.label}>{person.displayName || person.username}</Text>
                <Text numberOfLines={1} style={s.body}>@{person.username}</Text>
              </View>
              <Icon name={selected ? "check-circle" : "circle"} color={selected ? c.accentText : c.dim} />
            </Pressable>
          );
        })}
        <View style={[s.row, { flexWrap: "wrap" }]}>
          <Button title={editing ? "Save audience" : "Create circle"} busy={busy} icon="check" onPress={save} />
          {editing && <Button title="Cancel" quiet onPress={reset} />}
        </View>
      </View>
      <ResourceState
        resource={resource}
        empty={!list(resource.data).length}
        title="A circle for every part of you."
        body="Create a list, then select it under Custom audience when publishing a post."
        icon="users"
      >
        <View style={{ gap: 16 }}>
          {list(resource.data).map((circle) => (
            <View key={circle.id} style={[s.card, { padding: compact ? 20 : 28, gap: 18 }]}>
              <View style={s.row}>
                <View style={[s.emptyIcon, { width: 48, height: 48, borderRadius: 16, marginBottom: 0 }]}>
                  <Icon name="users" color={c.accentText} />
                </View>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={s.label}>{circle.name}</Text>
                  <Text style={s.body}>{circle.userIds?.length || 0} people</Text>
                </View>
              </View>
              {removing === circle.id ? (
                <>
                  <Text style={s.body}>Remove this circle? People whose access comes only from this list will lose access to those posts.</Text>
                  <View style={[s.row, { flexWrap: "wrap" }]}>
                    <Button title="Remove circle" small danger busy={busy} onPress={() => remove(circle.id)} />
                    <Button title="Keep it" small quiet onPress={() => setRemoving(null)} />
                  </View>
                </>
              ) : (
                <View style={[s.row, { flexWrap: "wrap" }]}>
                  <Button
                    title="Edit circle"
                    small
                    quiet
                    icon="edit-2"
                    onPress={() => {
                      setEditing(circle.id);
                      setName(circle.name);
                      setMembers(circle.userIds || []);
                      setError("");
                      setRemoving(null);
                    }}
                  />
                  <Button title="Remove" small quiet icon="trash-2" onPress={() => setRemoving(circle.id)} />
                </View>
              )}
            </View>
          ))}
        </View>
      </ResourceState>
    </>
  );
}
