import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const editor = await readFile(new URL('../src/studio/ArtworkEditor.tsx', import.meta.url), 'utf8')
const service = await readFile(new URL('../../../backend/gallery-server/src/main/java/com/ssolab/gallery/artwork/StudioArtworkService.java', import.meta.url), 'utf8')

test('Studio artwork form saves selected images through the same submit action', () => {
  assert.match(editor, /<form className="artwork-form"[\s\S]*type="file"[\s\S]*type="submit"/)
  assert.match(editor, /for \(const file of pendingFiles\) await studioApi\.upload/)
  assert.match(editor, /required=\{!id\}/)
})

test('image upload persists through the artwork aggregate without a duplicate repository save', () => {
  assert.match(service, /artwork\.addImage\(image\);\s*artworks\.flush\(\);\s*return GalleryDtos\.Image\.from\(image\)/)
  assert.doesNotMatch(service, /images\.save\(image\)/)
})
