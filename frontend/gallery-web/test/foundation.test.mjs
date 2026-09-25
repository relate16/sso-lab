import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')

test('gallery foundation keeps the public and Studio routes distinct', () => {
  for (const route of ['/', '/works', '/about', '/studio/*']) assert.match(app, new RegExp(route.replaceAll('*', '\\*')))
  assert.match(app, /Quiet Winter Gallery/)
})
