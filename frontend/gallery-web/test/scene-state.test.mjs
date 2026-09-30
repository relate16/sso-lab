import assert from 'node:assert/strict'
import test from 'node:test'
import { artworkDisplaySize, artworkMountPercent, artworkPhysicalMountPercent, fitPhysicalArtworkInPlane, fitPhysicalArtworkInWallRegion, isValidScene, nextScene, normalizedQuadPlacement, parseScene, projectHomographyPoint, rectangleToQuadHomography, rectangleToQuadMatrix } from '../src/gallery/sceneState.ts'
import { EXHIBITION_WALL_SIZE_CM, HOME_SLOTS, SCENE_DEFINITIONS } from '../src/gallery/sceneDefinitions.ts'

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
    assert.ok(SCENE_DEFINITIONS[scene].mount.centerX > 0)
    assert.ok(SCENE_DEFINITIONS[scene].mount.centerY > 0)
    assert.ok(SCENE_DEFINITIONS[scene].mount.width > 0)
    assert.ok(SCENE_DEFINITIONS[scene].mount.height > 0)
  }
})

test('Home exposes exactly four configured exhibition slots', () => {
  assert.deepEqual(HOME_SLOTS.map((slot) => slot.id), ['leftWall', 'centerLeft', 'centerRight', 'rightWall'])
  assert.deepEqual(HOME_SLOTS.map((slot) => slot.cornersPx), [
    { topLeft: [151, 248], topRight: [322, 278.7744], bottomRight: [321, 540.6849], bottomLeft: [154, 552] },
    { topLeft: [638, 317], topRight: [910, 305], bottomRight: [907, 531], bottomLeft: [637, 527] },
    { topLeft: [1032, 303], topRight: [1226, 292], bottomRight: [1223, 537], bottomLeft: [1036, 533] },
    { topLeft: [1363, 225], topRight: [1509, 169.6521], bottomRight: [1506, 590.5759], bottomLeft: [1368, 569] },
  ])
  assert.ok(HOME_SLOTS.every((slot) => Object.values(slot.corners).flat().every((value) => value > 0 && value < 1)))
  assert.ok(HOME_SLOTS.every((slot) => slot.mountCenter.x > 0 && slot.mountCenter.y > 0))
  assert.ok(HOME_SLOTS.every((slot) => Math.abs(slot.mountCenter.x - slot.lightCenter.x) < .01))
  assert.ok(HOME_SLOTS.every((slot) => slot.lightCenter.y < slot.mountCenter.y))
  assert.equal(HOME_SLOTS[0].shadowPreset, 'leftWall')
  assert.equal(HOME_SLOTS[3].shadowPreset, 'rightWall')
  assert.deepEqual(HOME_SLOTS[0].artworkCenter, { x: .5, y: .6 })
  assert.deepEqual(EXHIBITION_WALL_SIZE_CM, { width: 450, height: 300 })
  assert.ok(HOME_SLOTS.every((slot) => slot.wallRegionCm.width < EXHIBITION_WALL_SIZE_CM.width && slot.wallRegionCm.height < EXHIBITION_WALL_SIZE_CM.height))
  assert.deepEqual(HOME_SLOTS[3].artworkCenter, { x: .5, y: .62 })
  assert.deepEqual(HOME_SLOTS[0].horizontalVanishingPointPx, [1379, 469])
  assert.deepEqual(HOME_SLOTS[3].horizontalVanishingPointPx, [722, 468])
})

test('Home wall planes project all four rectangle corners onto configured geometry', () => {
  const width = 400
  const height = 280
  HOME_SLOTS.forEach((slot) => {
    const placement = normalizedQuadPlacement(slot.corners, slot.mountCenter)
    const matrix = rectangleToQuadMatrix(width, height, placement.localCorners)
    const values = matrix.slice('matrix3d('.length, -1).split(',').map(Number)
    const project = (x, y) => {
      const denominator = values[3] * x + values[7] * y + values[15]
      return [
        (values[0] * x + values[4] * y + values[12]) / denominator,
        (values[1] * x + values[5] * y + values[13]) / denominator,
      ]
    }
    const sources = [[0, 0], [width, 0], [width, height], [0, height]]
    const targets = Object.values(placement.localCorners).map(([x, y]) => [x * width, y * height])
    sources.forEach(([x, y], index) => {
      const projected = project(x, y)
      assert.ok(Math.abs(projected[0] - targets[index][0]) < .001, `${slot.id} x corner ${index}`)
      assert.ok(Math.abs(projected[1] - targets[index][1]) < .001, `${slot.id} y corner ${index}`)
    })
  })
})

test('mounted sizing preserves aspect and clamps artwork to its guide', () => {
  const portrait = artworkMountPercent(50, 100, 500, 330)
  const landscape = artworkMountPercent(100, 50, 500, 330)
  assert.ok(portrait.height > portrait.width)
  assert.ok(landscape.width > landscape.height)
  assert.ok(portrait.height <= 88)
  assert.ok(landscape.width <= 88)
  assert.ok(Math.abs((portrait.width * 500) / (portrait.height * 330) - .5) < .001)
  assert.ok(Math.abs((landscape.width * 500) / (landscape.height * 330) - 2) < .001)
})

test('detail wall sizing preserves physical differences and caps oversized mounts', () => {
  const small = artworkPhysicalMountPercent(48, 72, 450, 300)
  const medium = artworkPhysicalMountPercent(70, 99, 450, 300)
  const hundred = artworkPhysicalMountPercent(130.3, 162.2, 450, 300)
  const oversized = artworkPhysicalMountPercent(600, 800, 450, 300)
  assert.ok(small.width < medium.width && medium.width < hundred.width)
  assert.ok(small.height < medium.height && medium.height < hundred.height)
  assert.ok(Math.abs(small.width / small.height - (48 / 450) / (72 / 300)) < .001)
  assert.ok(oversized.width <= 90.001 && oversized.height <= 90.001)
})

test('detail wall sizing preserves physical differences for tiny works without minimum enlargement', () => {
  const five = artworkPhysicalMountPercent(5, 5, 450, 300, .9, 1672, 941)
  const ten = artworkPhysicalMountPercent(10, 10, 450, 300, .9, 1672, 941)
  assert.ok(five.width / 100 * 1672 < 64)
  assert.ok(Math.abs(ten.width / five.width - 2) < .001)
  assert.ok(Math.abs(ten.height / five.height - 2) < .001)
  assert.ok(Math.abs((five.width * 1672) / (five.height * 941) - 1) < .001)
})

test('Home wall sizing preserves exact physical ratios through 100-size works', () => {
  const small = fitPhysicalArtworkInWallRegion(30, 40, 300, 240, 173.33, 147.74, 450, 300)
  const large = fitPhysicalArtworkInWallRegion(120, 160, 300, 240, 173.33, 147.74, 450, 300)
  const hundred = fitPhysicalArtworkInWallRegion(130.3, 162.2, 300, 240, 173.33, 147.74, 450, 300)
  assert.ok(Math.abs(large.width / small.width - 4) < .001)
  assert.ok(Math.abs(large.height / small.height - 4) < .001)
  assert.ok(hundred.width > small.width)
  assert.ok(hundred.height > small.height)
  assert.ok(Math.abs((small.width / 300) / (small.height / 240) - (30 / 173.33) / (40 / 147.74)) < .001)
})

test('Home calibrates a small artwork against the full three-metre wall instead of the local slot', () => {
  const slot = HOME_SLOTS[1]
  const xs = Object.values(slot.cornersPx).map(([x]) => x)
  const ys = Object.values(slot.cornersPx).map(([, y]) => y)
  const rect = fitPhysicalArtworkInWallRegion(
    15.8,
    22.7,
    Math.max(...xs) - Math.min(...xs),
    Math.max(...ys) - Math.min(...ys),
    slot.wallRegionCm.width,
    slot.wallRegionCm.height,
    EXHIBITION_WALL_SIZE_CM.width,
    EXHIBITION_WALL_SIZE_CM.height,
  )
  assert.ok(rect.height > 30)
})

test('Home fits artwork before homography and preserves each wall plane vanishing point', () => {
  const artworkSizes = [[48, 72], [90, 60], [120, 150], [32, 32]]
  const intersection = (a, b, c, d) => {
    const determinant = (a[0] - b[0]) * (c[1] - d[1]) - (a[1] - b[1]) * (c[0] - d[0])
    return [
      ((a[0] * b[1] - a[1] * b[0]) * (c[0] - d[0]) - (a[0] - b[0]) * (c[0] * d[1] - c[1] * d[0])) / determinant,
      ((a[0] * b[1] - a[1] * b[0]) * (c[1] - d[1]) - (a[1] - b[1]) * (c[0] * d[1] - c[1] * d[0])) / determinant,
    ]
  }

  HOME_SLOTS.forEach((slot, index) => {
    const xs = Object.values(slot.cornersPx).map(([x]) => x)
    const ys = Object.values(slot.cornersPx).map(([, y]) => y)
    const width = Math.max(...xs) - Math.min(...xs)
    const height = Math.max(...ys) - Math.min(...ys)
    const placement = normalizedQuadPlacement(slot.corners, slot.mountCenter)
    const homography = rectangleToQuadHomography(width, height, placement.localCorners)
    assert.ok(homography)
    const [artworkWidth, artworkHeight] = artworkSizes[index]
    const rect = fitPhysicalArtworkInWallRegion(artworkWidth, artworkHeight, width, height, slot.wallRegionCm.width, slot.wallRegionCm.height, EXHIBITION_WALL_SIZE_CM.width, EXHIBITION_WALL_SIZE_CM.height, slot.artworkCenter)
    const actual = {
      topLeft: projectHomographyPoint(homography, [rect.left, rect.top]),
      topRight: projectHomographyPoint(homography, [rect.left + rect.width, rect.top]),
      bottomRight: projectHomographyPoint(homography, [rect.left + rect.width, rect.top + rect.height]),
      bottomLeft: projectHomographyPoint(homography, [rect.left, rect.top + rect.height]),
    }
    const target = Object.fromEntries(Object.entries(placement.localCorners).map(([name, [x, y]]) => [name, [x * width, y * height]]))
    const wallVanishingPoint = intersection(target.topLeft, target.topRight, target.bottomLeft, target.bottomRight)
    const artworkVanishingPoint = intersection(actual.topLeft, actual.topRight, actual.bottomLeft, actual.bottomRight)
    assert.ok(Math.hypot(wallVanishingPoint[0] - artworkVanishingPoint[0], wallVanishingPoint[1] - artworkVanishingPoint[1]) < .01, `${slot.id} horizontal vanishing point`)
    if (slot.horizontalVanishingPointPx) {
      const sourceVanishingPoint = intersection(slot.cornersPx.topLeft, slot.cornersPx.topRight, slot.cornersPx.bottomLeft, slot.cornersPx.bottomRight)
      assert.ok(Math.hypot(sourceVanishingPoint[0] - slot.horizontalVanishingPointPx[0], sourceVanishingPoint[1] - slot.horizontalVanishingPointPx[1]) < .01, `${slot.id} matches measured wall vanishing point`)
    }
    const center = slot.artworkCenter ?? { x: .5, y: .5 }
    assert.ok(Math.abs(rect.left + rect.width / 2 - width * center.x) < .001)
    assert.ok(Math.abs(rect.top + rect.height / 2 - height * center.y) < .001)
  })
})
