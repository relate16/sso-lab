import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
const viteConfig = await readFile(new URL('../vite.config.ts', import.meta.url), 'utf8')

test('gallery foundation keeps the public and Studio routes distinct', () => {
  for (const route of ['/', '/exhibition', '/works', '/about', '/studio/*']) assert.match(app, new RegExp(route.replaceAll('*', '\\*')))
  assert.match(app, /Quiet Winter Gallery/)
  assert.match(app, /<Link to="\/exhibition">Exhibition<\/Link>/)
  assert.match(app, /<Link to="\/">Home<\/Link>[\s\S]*<Link to="\/works">Works<\/Link>[\s\S]*<Link to="\/exhibition">Exhibition<\/Link>[\s\S]*<Link to="\/about">About<\/Link>/)
})

test('production build does not require files outside the Docker build context', () => {
  assert.match(viteConfig, /if \(command === 'build'\) return \{ plugins: \[reactPlugin\] \}/)
  assert.doesNotMatch(viteConfig, /^import .*vite\.shared\.mts/m)
  assert.match(viteConfig, /await import\(\/\* @vite-ignore \*\/ sharedConfigUrl\)/)
})
