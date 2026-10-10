import React, { useState } from "react";
import {
  ActivityIndicator,
  Image,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type ViewStyle,
} from "react-native";
import { Feather } from "@expo/vector-icons";
import { BlurView } from "expo-blur";
import { router, type Href } from "expo-router";
import { mediaUrl } from "../lib/api";
import { useTheme } from "../theme/ThemeProvider";
import { useViewport } from "../theme/useViewport";
import { radius, spacing, themes, type ThemeColors } from "../theme/tokens";

export const go = (path: string) => router.push(path as Href);

// Styles are built once per theme; consumers subscribe instead of sharing mutable colors.
export function useUI() {
  const theme = useTheme();
  const { width } = useViewport();
  return {
    ...theme,
    c: theme.colors,
    s: sharedStyles[theme.mode],
    compact: width < 600,
  };
}

export function Icon({
  name,
  size = 20,
  color,
}: {
  name: any;
  size?: number;
  color?: string;
}) {
  const { colors } = useTheme();
  return <Feather name={name} size={size} color={color ?? colors.muted} />;
}

export function Button({
  title,
  accessibilityLabel,
  onPress,
  icon,
  quiet,
  disabled,
  busy,
  small,
  danger,
}: {
  title: string;
  accessibilityLabel?: string;
  onPress: () => void;
  icon?: string;
  quiet?: boolean;
  disabled?: boolean;
  busy?: boolean;
  small?: boolean;
  danger?: boolean;
}) {
  const { c, s } = useUI();
  const foreground = disabled ? c.dim : danger ? c.danger : quiet ? c.text : c.onAccent;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityState={{ disabled: Boolean(disabled || busy), busy: Boolean(busy) }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed, hovered }: any) => [
        s.button,
        quiet && s.quiet,
        small && { paddingHorizontal: 14, minHeight: 44 },
        danger && { backgroundColor: c.dangerSoft, borderColor: c.danger },
        disabled && { backgroundColor: c.raised, borderWidth: 1, borderColor: c.line },
        busy && { opacity: 0.75 },
        hovered && { opacity: 0.88 },
        pressed && { opacity: 0.75, transform: [{ scale: 0.98 }] },
      ]}
    >
      {busy ? (
        <ActivityIndicator size="small" color={foreground} />
      ) : icon ? (
        <Icon name={icon} size={18} color={foreground} />
      ) : null}
      <Text style={[s.buttonText, { color: foreground }]}>{title}</Text>
    </Pressable>
  );
}

export function IconButton({
  name,
  label,
  onPress,
  active,
  disabled = false,
}: {
  name: string;
  label: string;
  onPress: () => void;
  active?: boolean;
  disabled?: boolean;
}) {
  const { c, s } = useUI();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected: active, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ hovered, pressed }: any) => [
        s.iconButton,
        active && { backgroundColor: c.accentSoft },
        (hovered || pressed) && { backgroundColor: c.raised },
        disabled && { opacity: 0.5 },
      ]}
    >
      <Icon name={name} color={active ? c.accentText : c.muted} />
    </Pressable>
  );
}

export function ThemeToggle() {
  const { mode, setMode } = useUI();
  return (
    <IconButton
      name={mode === "dark" ? "sun" : "moon"}
      label={`Switch to ${mode === "dark" ? "light" : "dark"} mode`}
      onPress={() => setMode(mode === "dark" ? "light" : "dark")}
    />
  );
}

export function GlassSurface({
  children,
  style,
}: React.PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  const { c, s, mode } = useUI();
  return (
    <View style={[s.card, { backgroundColor: c.glass }, style]}>
      {/* Android uses the tinted surface to avoid costly full-screen blur passes. */}
      {Platform.OS !== "android" && (
        <BlurView
          pointerEvents="none"
          intensity={28}
          tint={mode}
          style={StyleSheet.absoluteFill}
        />
      )}
      {children}
    </View>
  );
}

export function Avatar({ user, size = 42 }: { user?: any; size?: number }) {
  const { c } = useUI();
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const url = mediaUrl(user?.avatarUrl);
  return url && failedUrl !== url ? (
    <Image
      source={{ uri: url }}
      onError={() => setFailedUrl(url)}
      style={{ width: size, height: size, borderRadius: size / 2 }}
      accessibilityLabel={`${user?.displayName || user?.username || "Creator"} avatar`}
    />
  ) : (
    <View
      style={{
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: size / 2,
        backgroundColor: c.selected,
        alignItems: "center",
        justifyContent: "center",
        borderWidth: 1,
        borderColor: c.lineStrong,
      }}
    >
      <Text style={{ color: c.text, fontWeight: "700", fontSize: size * 0.36 }}>
        {(user?.displayName || user?.username || "D").slice(0, 1).toUpperCase()}
      </Text>
    </View>
  );
}

export function Field({
  label,
  containerStyle,
  floating = false,
  error,
  trailing,
  ...props
}: React.ComponentProps<typeof TextInput> & {
  label?: string;
  containerStyle?: StyleProp<ViewStyle>;
  floating?: boolean;
  error?: string;
  trailing?: React.ReactNode;
}) {
  const { c, s } = useUI();
  const [focused, setFocused] = useState(false);
  const raisedLabel = focused || Boolean(props.value);
  return (
    <View style={[{ gap: 8, minWidth: 0 }, containerStyle]}>
      {label && !floating ? <Text style={s.label}>{label}</Text> : null}
      <View>
        <TextInput
          placeholderTextColor={c.dim}
          selectionColor={c.accent}
          autoCapitalize="none"
          {...props}
          placeholder={floating && !raisedLabel ? "" : props.placeholder}
          accessibilityLabel={props.accessibilityLabel ?? label ?? props.placeholder}
          onFocus={(event) => {
            setFocused(true);
            props.onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            props.onBlur?.(event);
          }}
          style={[
            s.input,
            props.multiline && { minHeight: 120, textAlignVertical: "top" },
            floating && { height: 60, paddingTop: 25, paddingBottom: 8 },
            Boolean(trailing) && { paddingRight: 56 },
            props.style,
            focused && { borderColor: c.accentText },
            error && { borderColor: c.danger },
          ]}
        />
        {label && floating && (
          <Text
            pointerEvents="none"
            style={{
              position: "absolute",
              left: 16,
              top: raisedLabel ? 7 : 19,
              fontSize: raisedLabel ? 11 : 15,
              color: error ? c.danger : focused ? c.accentText : c.muted,
            }}
          >
            {label}
          </Text>
        )}
        {trailing && (
          <View style={{ position: "absolute", right: 4, top: 8 }}>
            {trailing}
          </View>
        )}
      </View>
      {error && (
        <Text accessibilityRole="alert" style={{ color: c.danger, fontSize: 12, lineHeight: 18 }}>
          {error}
        </Text>
      )}
    </View>
  );
}

export function ErrorNote({ message }: { message?: string }) {
  const { c, s } = useUI();
  return message ? (
    <View accessibilityRole="alert" style={s.error}>
      <Icon name="alert-circle" color={c.danger} size={18} />
      <Text style={{ color: c.danger, flex: 1, lineHeight: 22 }}>{message}</Text>
    </View>
  ) : null;
}

export function Empty({
  icon = "feather",
  title,
  body,
  action,
  onAction,
}: {
  icon?: string;
  title: string;
  body: string;
  action?: string;
  onAction?: () => void;
}) {
  const { c, s } = useUI();
  return (
    <View style={s.empty}>
      <View style={s.emptyIcon}>
        <Icon name={icon} size={28} color={c.accentText} />
      </View>
      <Text style={s.emptyTitle}>{title}</Text>
      <Text style={s.emptyBody}>{body}</Text>
      {action && onAction && (
        <Button title={action} onPress={onAction} icon="arrow-up-right" />
      )}
    </View>
  );
}

export function ResourceState({
  resource,
  children,
  empty = false,
  title = "A little quiet here",
  body = "Come back soon, or start a conversation of your own.",
  icon,
  action,
  onAction,
}: any) {
  const { s } = useUI();
  if (resource.loading) {
    return (
      <View accessibilityLabel="Loading content" accessibilityRole="progressbar" style={{ gap: 20 }}>
        {[0, 1].map((item) => (
          <View key={item} style={[s.card, { padding: 24, gap: 20 }]}>
            <View style={s.row}>
              <View style={s.skeletonAvatar} />
              <View style={[s.skeleton, { width: "45%" }]} />
            </View>
            <View style={s.skeleton} />
            <View style={[s.skeleton, { width: "65%" }]} />
            <View style={[s.skeleton, { height: 130 }]} />
          </View>
        ))}
      </View>
    );
  }
  if (resource.error) {
    return (
      <Empty
        icon="wifi-off"
        title="Let’s reconnect"
        body={resource.error}
        action="Try again"
        onAction={resource.reload}
      />
    );
  }
  if (empty) {
    return <Empty {...{ title, body, icon, action, onAction }} />;
  }
  return children;
}

export function Tag({
  text,
  active,
  onPress,
}: {
  text: string;
  active?: boolean;
  onPress?: () => void;
}) {
  const { c, s } = useUI();
  return (
    <Pressable
      disabled={!onPress}
      onPress={onPress}
      accessibilityRole={onPress ? "button" : undefined}
      accessibilityState={onPress ? { selected: active } : undefined}
      style={[
        s.tag,
        onPress && { minHeight: 44 },
        active && { borderColor: c.accent, backgroundColor: c.accentSoft },
      ]}
    >
      <Text style={{ color: active ? c.accentText : c.muted, fontSize: 13, fontWeight: "600" }}>
        {text}
      </Text>
    </Pressable>
  );
}

export function SectionTitle({
  title,
  subtitle,
  aside,
}: {
  title: string;
  subtitle?: string;
  aside?: React.ReactNode;
}) {
  const { s, compact } = useUI();
  return (
    <View style={[s.row, { flexWrap: "wrap", marginBottom: 28, gap: 16 }]}>
      <View style={{ flex: 1, minWidth: 180, gap: 8 }}>
        <Text accessibilityRole="header" style={[s.title, compact && { fontSize: 28, lineHeight: 34 }]}>
          {title}
        </Text>
        {subtitle && <Text style={s.body}>{subtitle}</Text>}
      </View>
      {aside}
    </View>
  );
}

function createStyles(c: ThemeColors) {
  return StyleSheet.create({
    row: {
      flexDirection: "row",
      alignItems: "center",
      gap: spacing.md,
    },
    card: {
      backgroundColor: c.panel,
      borderWidth: 1,
      borderColor: c.line,
      borderRadius: radius.card,
      overflow: "hidden",
    },
    title: {
      color: c.text,
      fontSize: 32,
      lineHeight: 40,
      fontWeight: "600",
      letterSpacing: -1,
    },
    body: {
      color: c.muted,
      fontSize: 16,
      lineHeight: 24,
    },
    label: {
      color: c.text,
      fontSize: 14,
      lineHeight: 21,
      fontWeight: "600",
    },
    eyebrow: {
      color: c.accentText,
      fontSize: 11,
      fontWeight: "700",
      letterSpacing: 2,
      lineHeight: 18,
    },
    input: {
      backgroundColor: c.input,
      color: c.text,
      borderWidth: 1,
      borderColor: c.lineStrong,
      borderRadius: radius.control,
      padding: 16,
      minHeight: 50,
      fontSize: 16,
      lineHeight: 23,
    },
    button: {
      minHeight: 50,
      flexShrink: 1,
      borderRadius: radius.control,
      backgroundColor: c.accent,
      flexDirection: "row",
      alignItems: "center",
      justifyContent: "center",
      paddingHorizontal: 20,
      paddingVertical: 12,
      gap: 9,
    },
    buttonText: {
      color: c.onAccent,
      fontWeight: "700",
      fontSize: 14,
      lineHeight: 20,
      flexShrink: 1,
      textAlign: "center",
    },
    quiet: {
      backgroundColor: "transparent",
      borderWidth: 1,
      borderColor: c.line,
    },
    iconButton: {
      width: 44,
      height: 44,
      alignItems: "center",
      justifyContent: "center",
      borderRadius: radius.control,
      flexShrink: 0,
    },
    error: {
      backgroundColor: c.dangerSoft,
      borderRadius: radius.control,
      padding: 16,
      flexDirection: "row",
      gap: 12,
      alignItems: "center",
    },
    empty: {
      alignItems: "center",
      paddingHorizontal: 20,
      paddingVertical: 44,
      gap: 16,
    },
    emptyIcon: {
      width: 64,
      height: 64,
      borderRadius: radius.card,
      backgroundColor: c.accentSoft,
      alignItems: "center",
      justifyContent: "center",
      marginBottom: 8,
    },
    emptyTitle: {
      color: c.text,
      fontSize: 24,
      lineHeight: 30,
      fontWeight: "600",
      letterSpacing: -0.5,
      textAlign: "center",
    },
    emptyBody: {
      color: c.muted,
      maxWidth: 360,
      lineHeight: 24,
      fontSize: 15,
      textAlign: "center",
      marginBottom: 8,
    },
    tag: {
      maxWidth: "100%",
      flexShrink: 1,
      borderWidth: 1,
      borderColor: c.line,
      borderRadius: radius.control,
      paddingHorizontal: 16,
      paddingVertical: 8,
      justifyContent: "center",
      alignItems: "center",
    },
    skeleton: {
      height: 14,
      borderRadius: radius.control,
      backgroundColor: c.raised,
    },
    skeletonAvatar: {
      width: 44,
      height: 44,
      borderRadius: 22,
      backgroundColor: c.raised,
    },
  });
}

const sharedStyles = {
  dark: createStyles(themes.dark),
  light: createStyles(themes.light),
};
