import React, { useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { usePathname } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Brand } from "./Brand";
import { SyncStatus } from "./SyncStatus";
import { useAuth } from "../lib/auth";
import { breakpoints, radius } from "../theme/tokens";
import { useViewport } from "../theme/useViewport";
import { Avatar, Button, go, Icon, IconButton, ThemeToggle, useUI } from "./ui";

const navigation = [
  ["/", "Home feed", "home"],
  ["/explore", "Search", "search"],
  ["/messages", "Messages", "message-circle"],
  ["/reels", "Reels", "play-circle"],
  ["/notifications", "Notifications", "bell"],
  ["/saved", "Saved", "bookmark"],
  ["/communities", "Communities", "users"],
];

export function Shell({
  children,
  title,
  aside,
}: React.PropsWithChildren<{ title?: string; aside?: React.ReactNode }>) {
  const { width } = useViewport();
  const insets = useSafeAreaInsets();
  const { c, s } = useUI();
  const { user } = useAuth();
  const path = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);
  const sidebar = width >= breakpoints.tablet;
  const expanded = width >= breakpoints.desktop;
  const showAside = Boolean(aside) && width >= breakpoints.wide;

  function navigate(route: string) {
    setMenuOpen(false);
    go(route);
  }

  function navItem(route: string, label: string, icon: string, full = expanded) {
    const active = path === route;
    return (
      <Pressable
        key={route}
        accessibilityRole="link"
        accessibilityLabel={label}
        accessibilityState={{ selected: active }}
        onPress={() => navigate(route)}
        style={({ hovered, pressed }: any) => [
          st.navItem,
          !full && { justifyContent: "center", paddingHorizontal: 0 },
          (hovered || pressed) && { backgroundColor: c.raised },
          active && { backgroundColor: c.raised },
        ]}
      >
        <Icon name={icon} size={21} color={active ? c.text : c.muted} />
        {full && (
          <Text style={{ color: active ? c.text : c.muted, fontSize: 14, fontWeight: active ? "600" : "400", flex: 1 }}>
            {label}
          </Text>
        )}
        {active && full && <View style={{ width: 4, height: 4, backgroundColor: c.accent, borderRadius: 2 }} />}
      </Pressable>
    );
  }

  return (
    <View accessibilityLabel={title} style={[st.app, { backgroundColor: c.bg }]}>
      {sidebar && (
        <View
          style={[
            st.sidebar,
            { width: expanded ? 240 : 88, borderColor: c.line, paddingBottom: Math.max(20, insets.bottom) },
          ]}
        >
          <View style={{ alignItems: expanded ? "flex-start" : "center", paddingHorizontal: expanded ? 8 : 0 }}>
            <Brand compact={!expanded} />
          </View>
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingTop: 40, gap: 4 }}>
            {navigation.map(([route, label, icon]) => navItem(route, label, icon))}
            <View style={{ marginTop: 24, marginBottom: 24 }}>
              {expanded ? (
                <Button title="Upload media" icon="plus" onPress={() => go(user ? "/create" : "/login")} />
              ) : (
                <IconButton name="plus" label="Upload media" active onPress={() => go(user ? "/create" : "/login")} />
              )}
            </View>
          </ScrollView>
          <View style={{ gap: 8 }}>
            {navItem("/settings", "Settings", "sliders")}
            <Pressable
              accessibilityRole="link"
              accessibilityLabel={user ? "Your profile" : "Log in"}
              accessibilityState={{ selected: path === "/profile" }}
              onPress={() => go(user ? "/profile" : "/login")}
              style={[s.row, { borderTopWidth: 1, borderColor: c.line, paddingTop: 20, marginTop: 8 }]}
            >
              <Avatar user={user} size={40} />
              {expanded && (
                <View style={{ flex: 1, minWidth: 0, gap: 2 }}>
                  <Text numberOfLines={1} style={s.label}>{user?.displayName || "Welcome to DBgram"}</Text>
                  <Text numberOfLines={1} style={{ color: c.dim, fontSize: 12 }}>
                    {user ? `@${user.username} · Profile` : "Log in to your account"}
                  </Text>
                </View>
              )}
            </Pressable>
          </View>
        </View>
      )}

      <View style={{ flex: 1, minWidth: 0 }}>
        <View style={[st.topbar, { borderColor: c.line, paddingHorizontal: sidebar ? 32 : 20 }]}>
          {sidebar ? <View style={{ flex: 1 }} /> : <Brand />}
          <View style={[s.row, { gap: 4 }]}>
            <ThemeToggle />
            {!sidebar && (
              <Pressable
                accessibilityRole="link"
                accessibilityLabel={user ? "Your profile" : "Log in"}
                onPress={() => go(user ? "/profile" : "/login")}
                style={s.iconButton}
              >
                <Avatar user={user} size={32} />
              </Pressable>
            )}
          </View>
        </View>
        <SyncStatus />
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={{
            alignItems: "center",
            flexGrow: 1,
            paddingHorizontal: sidebar ? 32 : 16,
            paddingTop: sidebar ? 40 : 24,
            paddingBottom: sidebar ? 40 + insets.bottom : 96 + insets.bottom,
          }}
        >
          <View style={{ flexDirection: "row", alignItems: "flex-start", gap: 48, width: "100%", maxWidth: showAside ? 980 : 720 }}>
            <View style={{ flex: 1, minWidth: 0 }}>{children}</View>
            {showAside && <View style={{ width: 264, paddingTop: 4 }}>{aside}</View>}
          </View>
        </ScrollView>

        {!sidebar && (
          <View style={[st.dock, { backgroundColor: c.glass, borderColor: c.line, paddingBottom: Math.max(8, insets.bottom) }]}>
            {[
              ["/", "Home", "home"],
              ["/explore", "Search", "search"],
              ["/create", "Upload media", "plus"],
              ["/messages", "Messages", "message-circle"],
              ["menu", "More", "menu"],
            ].map(([route, label, icon]) => (
              <Pressable
                key={route}
                accessibilityRole={route === "menu" ? "button" : "link"}
                accessibilityLabel={label}
                accessibilityState={{ selected: path === route, expanded: route === "menu" ? menuOpen : undefined }}
                onPress={() => route === "menu" ? setMenuOpen(true) : go(route)}
                style={st.dockItem}
              >
                <View style={[st.dockIcon, route === "/create" && { backgroundColor: c.accent }]}>
                  <Icon name={icon} size={22} color={route === "/create" ? c.onAccent : path === route ? c.text : c.dim} />
                </View>
                <Text style={{ color: path === route ? c.text : c.dim, fontSize: 10 }}>
                  {route === "/create" ? "Upload" : label}
                </Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>

      <Modal visible={menuOpen && !sidebar} transparent animationType="fade" onRequestClose={() => setMenuOpen(false)}>
        <View style={[st.modal, { backgroundColor: c.overlay, paddingTop: insets.top + 16 }]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Dismiss navigation"
            onPress={() => setMenuOpen(false)}
            style={StyleSheet.absoluteFill}
          />
          <View accessibilityViewIsModal style={[s.card, { padding: 24, paddingBottom: Math.max(24, insets.bottom), gap: 16, maxHeight: "90%" }]}>
            <View style={[s.row, { justifyContent: "space-between" }]}>
              <Text style={[s.title, { fontSize: 24 }]}>Your space</Text>
              <IconButton name="x" label="Close navigation" onPress={() => setMenuOpen(false)} />
            </View>
            <ScrollView>
              {navigation.filter(([route]) => !["/", "/explore", "/messages"].includes(route))
                .map(([route, label, icon]) => navItem(route, label, icon, true))}
              {navItem("/settings", "Settings", "sliders", true)}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

export function Gate({ children }: React.PropsWithChildren) {
  const { user, loading } = useAuth();
  const { c, s } = useUI();
  return user ? children : (
    <View style={[s.card, { padding: 32, gap: 20, alignItems: "flex-start" }]}>
      <Icon name="aperture" size={32} color={c.accentText} />
      <Text style={s.title}>{loading ? "Opening your space…" : "There’s a place for your perspective."}</Text>
      <Text style={s.body}>Log in to share your world and connect with the people who inspire you.</Text>
      {!loading && (
        <View style={[s.row, { flexWrap: "wrap" }]}>
          <Button title="Join DBgram" onPress={() => go("/register")} />
          <Button title="Log in" quiet onPress={() => go("/login")} />
        </View>
      )}
    </View>
  );
}

const st = StyleSheet.create({
  app: { flex: 1, flexDirection: "row" },
  sidebar: { padding: 20, paddingTop: 24, borderRightWidth: 1 },
  topbar: {
    minHeight: 80,
    paddingVertical: 12,
    borderBottomWidth: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  navItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    paddingHorizontal: 12,
    minHeight: 48,
    borderRadius: radius.control,
  },
  dock: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    borderTopWidth: 1,
    paddingTop: 8,
    paddingHorizontal: 8,
  },
  dockItem: { flex: 1, alignItems: "center", justifyContent: "center", gap: 4, minHeight: 48 },
  dockIcon: { width: 42, height: 30, alignItems: "center", justifyContent: "center", borderRadius: radius.control },
  modal: { flex: 1, justifyContent: "flex-end", padding: 12 },
});
