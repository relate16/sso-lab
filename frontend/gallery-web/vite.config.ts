import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { createFrontendViteConfig, repositoryRootFromConfigUrl } from '../vite.shared.mts'

export default defineConfig(({ command }) => createFrontendViteConfig({
  service: 'gallery',
  command,
  reactPlugin: react(),
  loadEnv,
  repoRoot: repositoryRootFromConfigUrl(import.meta.url),
}))
