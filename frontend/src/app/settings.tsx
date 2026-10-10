import { useState } from "react";
import { Pressable, Text, View } from "react-native";
import { Gate, Shell } from "../components/Shell";
import { Button, ErrorNote, go, Icon, SectionTitle, useUI } from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage } from "../lib/api";

export default function Settings() {
  const { c, s } = useUI();
  const { signOut } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function leave() {
    setBusy(true);
    try {
      await signOut();
      go("/");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title="Make yourself at home.">
      <Gate>
        <SectionTitle title="Your settings" subtitle="A little control over your corner of the world." />
        <View style={[s.card, { padding: 24, gap: 8 }]}>
          {[
            ["/profile", "Your profile", "Your photo, bio, and personal details", "user"],
            ["/privacy", "Privacy & circles", "Choose who can see your stories", "shield"],
            ["/analytics", "Creator studio", "A closer look at your audience and posts", "bar-chart-2"],
            ["/report", "Reports & safety", "Report a concern or manage a copyright claim", "flag"],
          ].map(([route, label, description, icon]) => (
            <Pressable key={route} accessibilityRole="link" onPress={() => go(route)} style={({ hovered }: any) => [s.row, { paddingVertical: 20, paddingHorizontal: 8, borderRadius: 4, backgroundColor: hovered ? c.raised : "transparent" }]}>
              <Icon name={icon} />
              <View style={{ flex: 1, gap: 4 }}>
                <Text style={s.label}>{label}</Text>
                <Text style={{ color: c.dim, fontSize: 13, lineHeight: 20 }}>{description}</Text>
              </View>
              <Icon name="chevron-right" size={16} />
            </Pressable>
          ))}
        </View>
        <View style={{ marginTop: 24, alignItems: "flex-start", gap: 16 }}>
          <ErrorNote message={error} />
          <Button title="Sign out" icon="log-out" quiet busy={busy} onPress={leave} />
        </View>
      </Gate>
    </Shell>
  );
}
