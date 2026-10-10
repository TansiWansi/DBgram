import { Slot } from "expo-router";
import Head from "expo-router/head";
import { StatusBar } from "expo-status-bar";
import { AuthProvider } from "../lib/auth";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { KeyboardAvoidingView, Platform } from "react-native";
import { ThemeProvider, useTheme } from "../theme/ThemeProvider";

export { ErrorBoundary } from "expo-router";

export default function Layout() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <AuthProvider>
          <ThemedLayout />
        </AuthProvider>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}

function ThemedLayout() {
  const { colors, mode } = useTheme();
  return (
    <SafeAreaView
      style={{ flex: 1, backgroundColor: colors.bg }}
      edges={["top", "left", "right"]}
    >
      <Head>
        <title>DBgram — A world worth sharing.</title>
        <meta name="theme-color" content={colors.bg} />
      </Head>
      <StatusBar style={mode === "dark" ? "light" : "dark"} />
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{ flex: 1 }}
      >
        <Slot />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
