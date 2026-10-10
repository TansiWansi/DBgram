import { useState } from "react";
import { Image, Pressable, ScrollView, Text, View } from "react-native";
import { router } from "expo-router";
import { FontAwesome } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Brand } from "../components/Brand";
import { Button, ErrorNote, Field, go, IconButton, ThemeToggle, useUI } from "../components/ui";
import { useAuth } from "../lib/auth";
import { errorMessage } from "../lib/api";
import { useViewport } from "../theme/useViewport";

export function AuthScreen({ register = false }: { register?: boolean }) {
  const { c, s, compact } = useUI();
  const { width } = useViewport();
  const insets = useSafeAreaInsets();
  const wide = width >= 960;
  const { signIn } = useAuth();
  const [fields, setFields] = useState({ username: "", email: "", displayName: "", usernameOrEmail: "", password: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [show, setShow] = useState(false);
  const complete = Boolean(fields.password && (register
    ? fields.username.trim() && fields.email.trim() && fields.displayName.trim()
    : fields.usernameOrEmail.trim()));

  function field(key: keyof typeof fields, value: string) {
    setFields((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => ({ ...previous, [key]: "" }));
    setError("");
  }

  async function submit() {
    if (busy) {
      return;
    }
    const next: Record<string, string> = {};
    if (register && !fields.displayName.trim()) {
      next.displayName = "Please enter your name.";
    }
    if (register && !/^\S+@\S+\.\S+$/.test(fields.email)) {
      next.email = "Enter a valid email address.";
    }
    if (register && !/^[A-Za-z0-9_]{3,30}$/.test(fields.username)) {
      next.username = "Use 3–30 letters, numbers, or underscores.";
    }
    if (!register && !fields.usernameOrEmail.trim()) {
      next.usernameOrEmail = "Enter your email or username.";
    }
    if (fields.password.length < 8) {
      next.password = "Use at least 8 characters.";
    }
    setErrors(next);
    setError("");
    if (Object.keys(next).length) {
      return;
    }
    setBusy(true);
    try {
      await signIn(fields, register);
      router.replace("/");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.bg }}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ flexGrow: 1, padding: compact ? 20 : 40, paddingBottom: Math.max(24, insets.bottom + 16) }}
    >
      <View style={{ maxWidth: 1120, width: "100%", alignSelf: "center", flex: 1 }}>
        <View style={[s.row, { justifyContent: "space-between", marginBottom: compact ? 36 : 40 }]}>
          <Brand />
          <ThemeToggle />
        </View>
        <View style={{ flexDirection: "row", width: "100%", maxWidth: wide ? 1120 : 480, alignSelf: "center", flex: 1 }}>
          {wide && (
            <View style={{ flex: 1, minWidth: 0, marginRight: 64, borderRadius: 4, overflow: "hidden", minHeight: 680 }}>
              <Image
                source={require("../../assets/editorial/architecture.jpg")}
                accessibilityLabel="Soft afternoon light and olive shadows on terracotta architecture"
                resizeMode="cover"
                style={{ width: "100%", height: "100%", position: "absolute" }}
              />
              <LinearGradient
                colors={["transparent", "rgba(15, 25, 19, 0.88)"]}
                style={{ flex: 1, padding: 40, paddingTop: 240, justifyContent: "flex-end" }}
              >
                <Text style={{ color: "#F4F1E9", fontSize: 10, letterSpacing: 2, marginBottom: 20 }}>A WORLD WORTH SHARING</Text>
                <Text style={{ color: "#FFFFFF", fontSize: 48, lineHeight: 52, fontWeight: "500", letterSpacing: -2 }}>
                  An eye for{"\n"}the everyday.
                </Text>
                <Text style={{ color: "#E2E5DA", fontSize: 15, lineHeight: 24, marginTop: 24, maxWidth: 310 }}>
                  The places, people, and little moments that make your world yours.
                </Text>
              </LinearGradient>
            </View>
          )}
          <View style={{ flex: 1, minWidth: 0, maxWidth: wide ? 440 : undefined, justifyContent: "center", paddingVertical: wide ? 16 : 0 }}>
            <View accessibilityRole="tablist" style={[s.row, { padding: 4, gap: 4, backgroundColor: c.raised, borderRadius: 4, marginBottom: 32 }]}>
              {[[false, "Log in"], [true, "Join DBgram"]].map(([joining, label]) => (
                <Pressable
                  key={String(label)}
                  accessibilityRole="tab"
                  accessibilityLabel={String(label)}
                  accessibilityState={{ selected: register === joining }}
                  onPress={() => router.replace(joining ? "/register" : "/login")}
                  style={({ hovered }: any) => ({ flex: 1, minHeight: 44, alignItems: "center", justifyContent: "center", borderRadius: 2, backgroundColor: register === joining ? c.panel : "transparent", opacity: hovered ? 0.8 : 1 })}
                >
                  <Text style={{ color: register === joining ? c.text : c.muted, fontSize: 14, fontWeight: "500" }}>{String(label)}</Text>
                </Pressable>
              ))}
            </View>
            <Text accessibilityRole="header" style={[s.title, { fontSize: compact ? 34 : 38, lineHeight: 44, marginBottom: 12 }]}>
              {register ? "A fresh perspective." : "Welcome back."}
            </Text>
            <Text style={[s.body, { marginBottom: 28 }]}>
              {register ? "Make a little space for your world." : "Your people. Your moments. Your space."}
            </Text>
            <View style={{ gap: 14 }}>
              <ErrorNote message={error} />
              {register ? (
                <>
                  <Field floating label="Your name" value={fields.displayName} onChangeText={(value) => field("displayName", value)} error={errors.displayName} autoComplete="name" maxLength={100} />
                  <Field floating label="Username" value={fields.username} onChangeText={(value) => field("username", value)} error={errors.username} autoComplete="username" maxLength={30} />
                  <Field floating label="Email address" value={fields.email} onChangeText={(value) => field("email", value)} error={errors.email} keyboardType="email-address" autoComplete="email" />
                </>
              ) : (
                <Field floating label="Email or username" value={fields.usernameOrEmail} onChangeText={(value) => field("usernameOrEmail", value)} error={errors.usernameOrEmail} autoComplete="username" />
              )}
              <Field
                floating
                label="Password"
                value={fields.password}
                onChangeText={(value) => field("password", value)}
                error={errors.password}
                secureTextEntry={!show}
                autoComplete={register ? "new-password" : "current-password"}
                onSubmitEditing={submit}
                trailing={<IconButton name={show ? "eye-off" : "eye"} label={show ? "Hide password" : "Show password"} onPress={() => setShow(!show)} />}
              />
              <View style={{ marginTop: 6 }}>
                <Button title="Continue" icon="arrow-right" busy={busy} disabled={!complete} onPress={submit} />
              </View>
            </View>
            <View style={[s.row, { gap: 16, marginVertical: 24 }]}>
              <View style={{ height: 1, flex: 1, backgroundColor: c.line }} />
              <Text style={{ color: c.dim, fontSize: 11 }}>OR</Text>
              <View style={{ height: 1, flex: 1, backgroundColor: c.line }} />
            </View>
            <View style={{ gap: 10 }}>
              {([['apple', 'Apple'], ['google', 'Google']] as const).map(([icon, label]) => (
                <Pressable
                  key={label}
                  disabled
                  accessibilityRole="button"
                  accessibilityLabel={`Continue with ${label} — coming soon`}
                  accessibilityState={{ disabled: true }}
                  style={[s.quiet, { minHeight: 48, borderRadius: 4, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 12, opacity: 0.55 }]}
                >
                  <FontAwesome name={icon} color={c.text} size={18} />
                  <Text style={{ color: c.text, fontSize: 13 }}>Continue with {label}</Text>
                </Pressable>
              ))}
              <Text style={{ color: c.dim, fontSize: 11, textAlign: "center", lineHeight: 18 }}>
                Email sign-in is ready. More ways to join are coming soon.
              </Text>
            </View>
            <Pressable accessibilityRole="link" onPress={() => go("/")} style={{ minHeight: 44, marginTop: 20, alignItems: "center", justifyContent: "center" }}>
              <Text style={{ color: c.muted, fontSize: 12 }}>← Back to DBgram</Text>
            </Pressable>
          </View>
        </View>
        <Text style={{ color: c.dim, fontSize: 10, letterSpacing: 1, paddingTop: 32, textAlign: "center" }}>YOUR PERSPECTIVE BELONGS HERE.</Text>
      </View>
    </ScrollView>
  );
}
