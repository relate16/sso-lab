import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const studio = await readFile(new URL('../src/studio/StudioApp.tsx', import.meta.url), 'utf8')
const security = await readFile(new URL('../../../backend/gallery-server/src/main/java/com/ssolab/gallery/config/GallerySecurityConfig.java', import.meta.url), 'utf8')

test('Artist Studio starts SSO automatically and returns to /studio after login', () => {
  assert.match(studio, /window\.location\.replace\('\/oauth2\/authorization\/gallery-client'\)/)
  assert.match(security, /new SimpleUrlAuthenticationSuccessHandler\("\/studio"\)/)
})

test('authenticated non-admin users can log out from the access denied screen', () => {
  assert.match(studio, /!state\.session\.roles\?\.includes\('ADMIN'\)[\s\S]*<LogoutForm csrf=\{state\.csrf\} prominent \/>/)
  assert.match(studio, /method="post" action="\/api\/v1\/logout"/)
  assert.match(studio, /name=\{csrf\.parameterName\} value=\{csrf\.token\}/)
})
