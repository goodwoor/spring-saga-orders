import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const gateway = process.env.VITE_GATEWAY_TARGET ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/order': { target: gateway, changeOrigin: true },
      '/inventory': { target: gateway, changeOrigin: true },
      '/payment': { target: gateway, changeOrigin: true },
    },
  },
});
