# DBgram editorial design system

The app defaults to Dark and keeps one sun/moon control in each page header.
`ThemeProvider` persists the preference in local storage on web and SecureStore
on native. Server markup and the first browser render both start in Dark.

## Visual language

Warm charcoal (`#171B19`), ivory (`#F4F1E9`), and muted clay (`#D9A38B`) frame the
photography. Botanical green and ocean blue belong to the editorial imagery.
Light mode uses paper surfaces and a deeper clay action color (`#A24D32`) for
contrast. No violet, purple, or neon accents are used.

`tokens.ts` defines semantic colors, spacing, breakpoints, and 4px component
corners. Avatars remain circular. Layout spacing follows an 8px rhythm, with 4px
optical adjustments. Typography uses the platform sans-serif stack, 16px body
copy, restrained weights, and tightly tracked display headlines.

Use `useUI()` for reactive colors and shared styles. Module-level styles may
contain geometry; theme-dependent styles must use the current semantic tokens.
Input focus and validation borders are explicit. Buttons expose disabled,
loading, hover, and pressed states. Keyboard focus remains visible on web.
Web transitions and loading pulses respect reduced-motion preferences.

## Layout and components

- `LandingScreen`: public editorial landing page, responsive navigation, framed
  cover photograph, feature section, and working footer destinations.
- `AuthScreen`: Login/Join tabs, floating labels, field-level errors, password
  visibility, and existing email authentication. Apple/Google buttons are
  explicitly disabled until provider authentication is implemented.
- `Shell`: bottom navigation below 900px, an 88px rail from 900px, and a 240px
  sidebar from 1180px. The feed's 264px discovery column appears from 1280px.
  Navigation contains one Upload action; additional destinations live in More.
- `PostView`: edge-to-edge square photos, lazy image loading and placeholders on
  web, media actions, captions, and comments. Rich structured posts retain their
  original block order and poll indices. Likes and saves use the existing API
  and offline queue.
- `DiscoveryAside`: real suggested accounts and existing follow endpoints. It
  never fabricates people, follower counts, engagement, or feed posts.
- `StoryTray` and `StoriesScreen`: compact story rings above the feed, photo/video
  uploads and text stories, a safe-area-aware viewer, progress, and owner controls.
  Private-account follow requests and visibility rules are enforced by the API;
  see [`STORIES_AND_PRIVACY.md`](../../../docs/STORIES_AND_PRIVACY.md).
- `settings`: links to existing profile, privacy, analytics, reporting, and
  sign-out flows. The sole theme control remains in the shell header.

`useViewport()` keeps static export hydration consistent before applying actual
device dimensions. The root handles top/side safe areas; mobile navigation
reserves space for the home indicator. Authentication handles its own bottom
inset. Wide and narrow layouts retain the same actions and backend permissions.

The two marketing photographs are bundled locally. See
[`assets/editorial/README.md`](../../assets/editorial/README.md) for their prompts
and provenance. They are marketing artwork, never inserted into real users'
feeds or profiles.
