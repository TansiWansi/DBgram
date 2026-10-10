import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import { themes, type ThemeColors, type ThemeMode } from "./tokens";

const STORAGE_KEY = "dbgram.appearance";

type ThemeContextValue = {
  mode: ThemeMode;
  colors: ThemeColors;
  setMode: (mode: ThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: React.PropsWithChildren) {
  // Dark is also the server-rendered default, keeping web hydration consistent.
  const [mode, updateMode] = useState<ThemeMode>("dark");
  const [restored, setRestored] = useState(false);
  const userSelected = useRef(false);
  const pendingWrite = useRef(Promise.resolve());

  useEffect(() => {
    let mounted = true;

    async function restorePreference() {
      try {
        const saved = Platform.OS === "web"
          ? window.localStorage.getItem(STORAGE_KEY)
          : await SecureStore.getItemAsync(STORAGE_KEY);

        // A slow native storage read must not undo a choice made after launch.
        if (mounted && !userSelected.current && (saved === "light" || saved === "dark")) {
          updateMode(saved);
        }
      } catch {
        // Private browsing or unavailable storage still permits an in-session theme.
      } finally {
        if (mounted) {
          setRestored(true);
        }
      }
    }

    void restorePreference();
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (Platform.OS === "web") {
      document.documentElement.dataset.theme = mode;
      document.documentElement.style.colorScheme = mode;
      document.querySelector('meta[name="theme-color"]')?.setAttribute("content", themes[mode].bg);
    }

    if (!restored) {
      return;
    }

    // Serialize native writes so rapid toggles persist the last selected theme.
    pendingWrite.current = pendingWrite.current.then(async () => {
      try {
        if (Platform.OS === "web") {
          window.localStorage.setItem(STORAGE_KEY, mode);
        } else {
          await SecureStore.setItemAsync(STORAGE_KEY, mode);
        }
      } catch {
        // Appearance remains usable when device storage is unavailable.
      }
    });
  }, [mode, restored]);

  const setMode = useCallback((nextMode: ThemeMode) => {
    userSelected.current = true;
    updateMode(nextMode);
  }, []);

  return (
    <ThemeContext.Provider value={{ mode, colors: themes[mode], setMode }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used inside ThemeProvider");
  }
  return context;
}
