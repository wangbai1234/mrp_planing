import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5180,
    proxy: {
      '/api': {
        target: process.env.MRP_VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
