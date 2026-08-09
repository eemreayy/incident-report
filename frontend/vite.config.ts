/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The browser talks to the API on its own origin (ADR-025). In production nginx
 * does that; in development this proxy does, so both environments behave the
 * same way and no absolute API address exists anywhere in the source.
 *
 * The SSE endpoint lives under /api/v1/stream and needs no special handling
 * here - Vite's proxy streams responses rather than buffering them. The
 * production nginx config is where buffering has to be turned off explicitly.
 */
const API_TARGET = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: true },
      '/actuator/health': { target: API_TARGET, changeOrigin: true },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        // Bootstrap only - it mounts the tree and asserts nothing, exactly like
        // the Spring application's main() is excluded from JaCoCo (ADR-018).
        'src/main.tsx',
        '**/*.test.{ts,tsx}',
        '**/*.d.ts',
      ],
      // Mirrors the backend gate: 80% lines, and falling below breaks the
      // build rather than printing a warning (ADR-024).
      //
      // Worth knowing when reading the number: a component whose body is a
      // single JSX return counts as one line, so the view layer contributes
      // almost nothing to the total and the figure is dominated by the logic
      // files. That is the intended shape (ADR-024) - the API client and the
      // state layer are where the gate has teeth. Views are held by behaviour
      // tests, which the number cannot see.
      thresholds: { lines: 80 },
    },
  },
});
