// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
module.exports = defineConfig([
  expoConfig,
  { ignores: ['dist/**', '.expo/**'] },
  {
    // Resource loaders and draft hydration intentionally reset UI state when the
    // account or route changes. Keep that transition visible while requests run.
    rules: { 'react-hooks/set-state-in-effect': 'off' },
  },
]);
