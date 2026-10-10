// Restrained editorial colors. Action and text tokens carry theme-specific contrast.
export const palette = {
  ink: "#191D1B",
  ocean: "#344D53",
  moss: "#57644E",
  paper: "#F4F1E9",
  clay: "#D9A38B",
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  section: 48,
} as const;

export const radius = {
  control: 4,
  card: 4,
  hero: 4,
  pill: 999,
} as const;

export const breakpoints = {
  tablet: 900,
  desktop: 1180,
  wide: 1280,
} as const;

const dark = {
  ...palette,
  bg: "#171B19",
  panel: "#1D221F",
  raised: "#29302B",
  input: "#191E1B",
  glass: "rgba(23, 27, 25, 0.94)",
  line: "#323A34",
  lineStrong: "#586258",
  text: palette.paper,
  muted: "#B2B8AE",
  dim: "#9AA397",
  accent: palette.clay,
  accentText: "#E6B7A1",
  accentSoft: "#332C25",
  onAccent: "#292018",
  secondary: palette.paper,
  onSecondary: palette.ink,
  selected: "#354137",
  danger: "#F3A197",
  dangerSoft: "#3B2723",
  overlay: "rgba(10, 15, 11, 0.74)",
  shadow: "rgba(0, 0, 0, 0.18)",
};

export type ThemeMode = "dark" | "light";
export type ThemeColors = { [Key in keyof typeof dark]: string };

const light: ThemeColors = {
  ...dark,
  bg: "#F6F5F0",
  panel: "#FFFDF8",
  raised: "#EAECE4",
  input: "#FFFDF8",
  glass: "rgba(246, 245, 240, 0.94)",
  line: "#DCDFD5",
  lineStrong: "#A0AA9A",
  text: "#242B24",
  muted: "#5D685C",
  dim: "#6A7467",
  accent: "#A24D32",
  accentText: "#95442D",
  accentSoft: "#F2E3D9",
  onAccent: "#FFFFFF",
  secondary: "#273B2D",
  onSecondary: "#FFFDF8",
  selected: "#DDE5D8",
  danger: "#B23C32",
  dangerSoft: "#F9E8E3",
  overlay: "rgba(19, 28, 20, 0.48)",
  shadow: "rgba(24, 39, 25, 0.08)",
};

export const themes: Record<ThemeMode, ThemeColors> = { dark, light };
