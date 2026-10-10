import { useRef } from "react";
import { Image, Pressable, ScrollView, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Brand } from "../components/Brand";
import { Button, go, Icon, ThemeToggle, useUI } from "../components/ui";
import { useViewport } from "../theme/useViewport";

export function LandingScreen() {
  const { c, s, compact } = useUI();
  const { width } = useViewport();
  const insets = useSafeAreaInsets();
  const wide = width >= 960;
  const scroll = useRef<ScrollView>(null);
  const featureOffset = useRef(0);
  const explore = () => scroll.current?.scrollTo({ y: featureOffset.current, animated: true });

  return (
    <ScrollView
      ref={scroll}
      style={{ flex: 1, backgroundColor: c.bg }}
      contentContainerStyle={{ paddingBottom: Math.max(24, insets.bottom) }}
    >
      <View style={{ paddingHorizontal: compact ? 20 : 48, borderBottomWidth: 1, borderColor: c.line }}>
        <View style={[s.row, { maxWidth: 1240, alignSelf: "center", width: "100%", minHeight: 88, justifyContent: "space-between" }]}>
          <Brand />
          {wide && (
            <View style={[s.row, { gap: 32 }]}>
              <NavLink label="Discover DBgram" onPress={explore} />
              <NavLink label="The community" onPress={() => go("/communities")} />
            </View>
          )}
          <View style={[s.row, { gap: compact ? 0 : 12 }]}>
            <ThemeToggle />
            <NavLink label="Log in" onPress={() => go("/login")} />
            {!compact && <Button title="Join DBgram" small onPress={() => go("/register")} />}
          </View>
        </View>
      </View>

      <View style={{ maxWidth: 1240, width: "100%", alignSelf: "center", paddingHorizontal: compact ? 20 : 48 }}>
        <View style={{ flexDirection: wide ? "row" : "column", gap: wide ? 48 : 40, paddingTop: compact ? 48 : 64, paddingBottom: compact ? 56 : 80 }}>
          <View style={{ flex: wide ? 1 : undefined, justifyContent: "center", alignItems: "flex-start", minWidth: 0 }}>
            <View style={[s.row, { gap: 10, marginBottom: 32 }]}>
              <View style={{ width: 24, height: 1, backgroundColor: c.accent }} />
              <Text style={[s.eyebrow, { fontSize: 10, letterSpacing: 1.7 }]}>A PLACE FOR YOUR PERSPECTIVE</Text>
            </View>
            <Text
              accessibilityRole="header"
              style={{ color: c.text, fontSize: compact ? 58 : wide ? 84 : 80, lineHeight: compact ? 62 : 88, letterSpacing: -3.5, fontWeight: "500" }}
            >
              Visuals,{"\n"}<Text style={{ color: c.accentText }}>Elevated.</Text>
            </Text>
            <Text style={[s.body, { maxWidth: 370, fontSize: compact ? 17 : 18, lineHeight: 29, marginTop: 28, marginBottom: 32 }]}>
              For the moments you notice.{"\n"}
              And the people who see them, too.
            </Text>
            <Button title="Join the community" icon="arrow-up-right" onPress={() => go("/register")} />
            <Text style={{ color: c.dim, fontSize: 12, marginTop: 16 }}>Your perspective belongs here. Free to join.</Text>
            <View style={[s.row, { gap: 16, borderTopWidth: 1, borderColor: c.line, paddingTop: 24, marginTop: wide ? 64 : 32 }]}>
              <Icon name="aperture" size={23} color={c.muted} />
              <View style={{ gap: 4 }}>
                <Text style={{ color: c.text, fontSize: 13 }}>Less noise. More perspective.</Text>
                <Text style={{ color: c.dim, fontSize: 12 }}>Photography. People. Everything in between.</Text>
              </View>
            </View>
          </View>

          <View style={{ flex: wide ? 1.1 : undefined, minWidth: 0, width: "100%", maxWidth: wide ? 560 : 680, alignSelf: "center" }}>
            <View style={{ borderWidth: 1, borderColor: c.lineStrong, padding: 8, borderRadius: 4, backgroundColor: c.panel }}>
              <View style={{ aspectRatio: 0.88, overflow: "hidden", borderRadius: 2 }}>
                <Image
                  source={require("../../assets/editorial/coast.jpg")}
                  accessibilityLabel="Sunlit cliffs above a quiet turquoise cove"
                  resizeMode="cover"
                  style={{ width: "100%", height: "100%" }}
                />
                <LinearGradient
                  colors={["transparent", "rgba(12, 25, 25, 0.74)"]}
                  style={{ position: "absolute", bottom: 0, left: 0, right: 0, padding: 24, paddingTop: 80 }}
                >
                  <Text style={{ color: "#FFFFFF", fontSize: 11, letterSpacing: 2, marginBottom: 8 }}>THE ART OF NOTICING</Text>
                  <Text style={{ color: "#FFFFFF", fontSize: 25, fontWeight: "500", letterSpacing: -0.6 }}>A little closer to the world.</Text>
                </LinearGradient>
                <View style={{ position: "absolute", right: 16, top: 16, padding: 8, backgroundColor: "rgba(15, 25, 23, 0.6)", borderRadius: 4 }}>
                  <Icon name="image" size={16} color="#FFFFFF" />
                </View>
              </View>
              <View style={[s.row, { justifyContent: "space-between", paddingTop: 12, paddingBottom: 4, paddingHorizontal: 8 }]}>
                <Text style={{ color: c.dim, fontSize: 10, letterSpacing: 1.4 }}>01 / A DIFFERENT POINT OF VIEW</Text>
                <Icon name="aperture" color={c.accentText} size={16} />
              </View>
            </View>
          </View>
        </View>

        <View style={{ borderTopWidth: 1, borderBottomWidth: 1, borderColor: c.line, paddingVertical: 24, flexDirection: compact ? "column" : "row", flexWrap: "wrap", gap: 24, justifyContent: "space-between" }}>
          {[
            ["image", "Your world, in full frame"],
            ["users", "Find your kind of people"],
            ["lock", "Choose who sees your story"],
          ].map(([icon, label]) => (
            <View key={label} style={[s.row, { gap: 12 }]}>
              <Icon name={icon} size={18} color={c.accentText} />
              <Text style={{ color: c.muted, fontSize: 13 }}>{label}</Text>
            </View>
          ))}
        </View>

        <View
          onLayout={(event) => { featureOffset.current = event.nativeEvent.layout.y + 88; }}
          style={{ flexDirection: wide ? "row" : "column", gap: wide ? 80 : 32, paddingVertical: compact ? 56 : 88, alignItems: "center" }}
        >
          <View style={{ flex: wide ? 1 : undefined, width: "100%", maxWidth: 520 }}>
            <View style={{ width: "100%", aspectRatio: 1.08, borderRadius: 4, overflow: "hidden" }}>
              <Image
                source={require("../../assets/editorial/architecture.jpg")}
                accessibilityLabel="Olive-branch shadows on a terracotta wall and a deep green doorway"
                style={{ width: "100%", height: "100%" }}
                resizeMode="cover"
              />
            </View>
            <View style={[s.row, { justifyContent: "space-between", marginTop: 12 }]}>
              <Text style={{ color: c.dim, fontSize: 10, letterSpacing: 1.3 }}>SMALL DETAILS. FULL STORIES.</Text>
              <Text style={{ color: c.dim, fontSize: 10 }}>DBGRAM / EDITORIAL</Text>
            </View>
          </View>
          <View style={{ flex: wide ? 1 : undefined, width: "100%", maxWidth: 520, gap: 24 }}>
            <Text style={s.eyebrow}>MORE THAN A SCROLL</Text>
            <Text accessibilityRole="header" style={{ color: c.text, fontSize: compact ? 36 : 44, lineHeight: compact ? 42 : 50, letterSpacing: -1.5, fontWeight: "500" }}>
              See something.{"\n"}Feel something.{"\n"}Share something.
            </Text>
            <Text style={[s.body, { maxWidth: 360 }]}>
              The good light on your walk home. A place you never want to forget.
              DBgram gives your everyday moments room to breathe.
            </Text>
            <View style={{ gap: 20, marginTop: 8 }}>
              <Feature number="01" title="Make it yours" description="Photos, films, and words. Tell the story your way." />
              <Feature number="02" title="Find a connection" description="Follow a fresh perspective. Start a real conversation." />
              <Feature number="03" title="Share on your terms" description="Post for everyone, your close friends, or just yourself." />
            </View>
          </View>
        </View>

        <View style={{ borderTopWidth: 1, borderColor: c.line, paddingTop: 32, paddingBottom: 16, flexDirection: compact ? "column" : "row", alignItems: compact ? "flex-start" : "center", justifyContent: "space-between", gap: 20 }}>
          <View style={{ gap: 8 }}>
            <Text style={{ color: c.text, fontSize: 17, fontWeight: "600", letterSpacing: -0.5 }}>dbgram.</Text>
            <Text style={{ color: c.dim, fontSize: 11 }}>© {new Date().getFullYear()} DBgram. A world worth sharing.</Text>
          </View>
          <View style={[s.row, { flexWrap: "wrap", gap: 16 }]}>
            <NavLink label="About" onPress={explore} />
            <NavLink label="Privacy controls" onPress={() => go("/privacy")} />
            <NavLink label="Community" onPress={() => go("/communities")} />
          </View>
        </View>
      </View>
    </ScrollView>
  );
}

function NavLink({ label, onPress }: { label: string; onPress: () => void }) {
  const { c } = useUI();
  return (
    <Pressable
      accessibilityRole="link"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ hovered, pressed }: any) => ({ minHeight: 44, paddingHorizontal: 8, justifyContent: "center", opacity: hovered || pressed ? 0.6 : 1 })}
    >
      <Text style={{ color: c.muted, fontSize: 13 }}>{label}</Text>
    </Pressable>
  );
}

function Feature({ number, title, description }: { number: string; title: string; description: string }) {
  const { c, s } = useUI();
  return (
    <View style={[s.row, { alignItems: "flex-start", gap: 20 }]}>
      <Text style={{ color: c.accentText, fontSize: 11, paddingTop: 3 }}>{number}</Text>
      <View style={{ flex: 1, gap: 5 }}>
        <Text style={s.label}>{title}</Text>
        <Text style={{ color: c.dim, fontSize: 13, lineHeight: 20 }}>{description}</Text>
      </View>
    </View>
  );
}
