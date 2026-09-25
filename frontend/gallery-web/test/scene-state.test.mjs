import assert from 'node:assert/strict'
import test from 'node:test'
import { artworkDisplaySize, isValidScene, nextScene, parseScene } from '../src/gallery/sceneState.ts'

test('scene selection cycles deterministically', () => {
  assert.equal(nextScene(1), 2)
  assert.equal(nextScene(2), 3)
  assert.equal(nextScene(3), 1)
})

test('scene URLs accept only the three deep-link values', () => {
  assert.equal(parseScene('2'), 2)
  assert.equal(parseScene('9'), 1)
  assert.equal(isValidScene('3'), true)
  assert.equal(isValidScene(null), false)
})

test('relative artwork sizing preserves the original aspect ratio', () => {
  const small = artworkDisplaySize(20, 40)
  const large = artworkDisplaySize(100, 200)
  assert.equal(small.height / small.width, 2)
  assert.equal(large.height / large.width, 2)
  assert.ok(large.height > small.height)
  assert.ok(large.height <= 500)
})
