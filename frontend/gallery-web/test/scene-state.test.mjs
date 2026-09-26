import assert from 'node:assert/strict'
import test from 'node:test'
import { artworkDisplaySize, artworkMountPercent, isValidScene, nextScene, parseScene } from '../src/gallery/sceneState.ts'
import { HOME_SLOTS, SCENE_DEFINITIONS } from '../src/gallery/sceneDefinitions.ts'

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

test('approved assets map to all three URL-backed scenes', () => {
  for (const scene of [1, 2, 3]) {
    assert.match(SCENE_DEFINITIONS[scene].asset, new RegExp(`gallery-detail-scene-${scene}\\.webp$`))
    assert.ok(SCENE_DEFINITIONS[scene].mount.width > 0)
    assert.ok(SCENE_DEFINITIONS[scene].mount.height > 0)
  }
})

test('Home exposes exactly four configured exhibition slots', () => {
  assert.deepEqual(HOME_SLOTS.map((slot) => slot.id), ['leftWall', 'centerLeft', 'centerRight', 'rightWall'])
  assert.notEqual(HOME_SLOTS[0].transform, HOME_SLOTS[1].transform)
  assert.notEqual(HOME_SLOTS[3].transform, HOME_SLOTS[2].transform)
})

test('mounted sizing preserves aspect and clamps artwork to its guide', () => {
  const portrait = artworkMountPercent(50, 100, 500, 330)
  const landscape = artworkMountPercent(100, 50, 500, 330)
  assert.ok(portrait.height > portrait.width)
  assert.ok(landscape.width > landscape.height)
  assert.ok(portrait.height <= 84)
  assert.ok(landscape.width <= 84)
  assert.ok(Math.abs((portrait.width * 500) / (portrait.height * 330) - .5) < .001)
  assert.ok(Math.abs((landscape.width * 500) / (landscape.height * 330) - 2) < .001)
})
