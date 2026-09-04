import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8084';

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/media': {
          // 必须与本地后端 MINIO_ENDPOINT 一致，以保留签名所需的 Host。
          target: env.VITE_MINIO_PROXY_TARGET || 'http://localhost:9000',
          changeOrigin: true,
          rewrite: path => path.replace(/^\/media/, ''),
        },
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  };
})
