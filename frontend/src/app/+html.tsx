import { ScrollViewStyleReset } from "expo-router/html";
import type { ReactNode } from "react";
export default function Root({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
        <meta name="theme-color" content="#171B19" />
        <meta
          name="description"
          content="A place for your perspective. Share photography, everyday moments, and stories with the people who inspire you on DBgram."
        />
        <title>DBgram — A world worth sharing.</title>
        <ScrollViewStyleReset />
        <style
          dangerouslySetInnerHTML={{
            __html: `
              :root {
                color-scheme: dark;
                --page: #171B19;
                --ink: #F4F1E9;
                --scrollbar: #586258;
                --accent: #D9A38B;
              }
              :root[data-theme="light"] {
                color-scheme: light;
                --page: #F6F5F0;
                --ink: #242B24;
                --scrollbar: #A0AA9A;
                --accent: #A24D32;
              }
              html, body, #root { background: var(--page); color: var(--ink); }
              * { box-sizing: border-box; }
              body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
              :focus-visible { outline: 2px solid var(--accent); outline-offset: 4px; }
              ::-webkit-scrollbar { width: 6px; height: 6px; }
              ::-webkit-scrollbar-thumb { background: var(--scrollbar); border-radius: 6px; }
              ::selection { background: var(--accent); color: var(--page); }
              button, a, [role="button"], [role="link"] {
                touch-action: manipulation;
                transition: background-color 160ms ease, opacity 160ms ease, transform 180ms ease;
              }
              [aria-label="Loading photo"] { animation: dbgram-loading 1.2s ease-in-out infinite alternate; }
              @keyframes dbgram-loading { from { opacity: 0.6; } to { opacity: 1; } }
              @media (prefers-reduced-motion: reduce) {
                * { animation: none !important; transition: none !important; scroll-behavior: auto !important; }
              }
            `,
          }}
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
