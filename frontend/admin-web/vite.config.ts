import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(async ({ command }) => {
  const reactPlugin = react()
  if (command === 'build') return { plugins: [reactPlugin] }
  const sharedConfigUrl = new URL('../vite.shared.mts', import.meta.url).href
  const shared = await import(/* @vite-ignore */ sharedConfigUrl)
  return shared.createFrontendViteConfig({
    service: 'admin', command, reactPlugin, loadEnv,
    repoRoot: shared.repositoryRootFromConfigUrl(import.meta.url),
  })
})
