export type SceneNumber = 1 | 2 | 3

export type NormalizedPoint = readonly [x: number, y: number]

export type NormalizedQuad = {
  topLeft: NormalizedPoint
  topRight: NormalizedPoint
  bottomRight: NormalizedPoint
  bottomLeft: NormalizedPoint
}

export type NormalizedCenter = { x: number; y: number }

export type Homography = readonly [number, number, number, number, number, number, number, number]

export type PlaneRect = { left: number; top: number; width: number; height: number }

export function normalizedQuadPlacement(corners: NormalizedQuad, mountCenter: NormalizedCenter) {
  const points = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft]
  const minX = Math.min(...points.map(([x]) => x))
  const maxX = Math.max(...points.map(([x]) => x))
  const minY = Math.min(...points.map(([, y]) => y))
  const maxY = Math.max(...points.map(([, y]) => y))
  const width = maxX - minX
  const height = maxY - minY
  const left = mountCenter.x - width / 2
  const top = mountCenter.y - height / 2
  const localize = ([x, y]: NormalizedPoint): NormalizedPoint => [(x - left) / width, (y - top) / height]

  return {
    left,
    top,
    width,
    height,
    localCorners: {
      topLeft: localize(corners.topLeft),
      topRight: localize(corners.topRight),
      bottomRight: localize(corners.bottomRight),
      bottomLeft: localize(corners.bottomLeft),
    },
  }
}

function solveLinearSystem(rows: number[][], values: number[]) {
  const matrix = rows.map((row, index) => [...row, values[index]])
  const size = values.length

  for (let column = 0; column < size; column += 1) {
    let pivot = column
    for (let row = column + 1; row < size; row += 1) {
      if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row
    }
    if (Math.abs(matrix[pivot][column]) < 1e-10) throw new Error('Home artwork wall plane is not projectable')
    ;[matrix[column], matrix[pivot]] = [matrix[pivot], matrix[column]]

    const divisor = matrix[column][column]
    for (let item = column; item <= size; item += 1) matrix[column][item] /= divisor

    for (let row = 0; row < size; row += 1) {
      if (row === column) continue
      const factor = matrix[row][column]
      for (let item = column; item <= size; item += 1) matrix[row][item] -= factor * matrix[column][item]
    }
  }

  return matrix.map((row) => row[size])
}

/** Solves one projective transform from a canonical rectangle to all four wall-plane corners. */
export function rectangleToQuadHomography(width: number, height: number, corners: NormalizedQuad): Homography | null {
  if (width <= 0 || height <= 0) return null
  const sources: NormalizedPoint[] = [[0, 0], [width, 0], [width, height], [0, height]]
  const targets: NormalizedPoint[] = [
    [corners.topLeft[0] * width, corners.topLeft[1] * height],
    [corners.topRight[0] * width, corners.topRight[1] * height],
    [corners.bottomRight[0] * width, corners.bottomRight[1] * height],
    [corners.bottomLeft[0] * width, corners.bottomLeft[1] * height],
  ]
  const rows: number[][] = []
  const values: number[] = []

  sources.forEach(([x, y], index) => {
    const [targetX, targetY] = targets[index]
    rows.push([x, y, 1, 0, 0, 0, -targetX * x, -targetX * y])
    values.push(targetX)
    rows.push([0, 0, 0, x, y, 1, -targetY * x, -targetY * y])
    values.push(targetY)
  })

  return solveLinearSystem(rows, values) as unknown as Homography
}

export function projectHomographyPoint(homography: Homography, [x, y]: NormalizedPoint): NormalizedPoint {
  const [a, b, c, d, e, f, g, h] = homography
  const divisor = g * x + h * y + 1
  return [(a * x + b * y + c) / divisor, (d * x + e * y + f) / divisor]
}

export function homographyToMatrix3d(homography: Homography | null) {
  if (!homography) return 'none'
  const [a, b, c, d, e, f, g, h] = homography
  const clean = (value: number) => Math.abs(value) < 1e-10 ? 0 : Number(value.toFixed(10))
  return `matrix3d(${[
    a, d, 0, g,
    b, e, 0, h,
    0, 0, 1, 0,
    c, f, 0, 1,
  ].map(clean).join(',')})`
}

/** Maps a responsive rectangular mount plane onto four measured wall points. */
export function rectangleToQuadMatrix(width: number, height: number, corners: NormalizedQuad) {
  return homographyToMatrix3d(rectangleToQuadHomography(width, height, corners))
}

export function parseScene(value: string | null): SceneNumber {
  return value === '2' || value === '3' ? Number(value) as SceneNumber : 1
}

export function isValidScene(value: string | null) {
  return value === '1' || value === '2' || value === '3'
}

export function nextScene(scene: SceneNumber): SceneNumber {
  return scene === 3 ? 1 : scene + 1 as SceneNumber
}

export function artworkDisplaySize(widthCm: number, heightCm: number) {
  const safeWidth = Math.max(.1, widthCm)
  const safeHeight = Math.max(.1, heightCm)
  const largest = Math.max(safeWidth, safeHeight)
  const minimumScale = 96 / largest
  const maximumScale = 500 / largest
  const scale = Math.min(maximumScale, Math.min(6, Math.max(3.6, minimumScale)))
  return { width: Math.round(safeWidth * scale), height: Math.round(safeHeight * scale) }
}

export function artworkMountPercent(
  widthCm: number,
  heightCm: number,
  mountWidthPx: number,
  mountHeightPx: number,
  mode: 'relative' | 'home' = 'relative',
  homeFillRange: { minFill: number; maxFill: number } = { minFill: .66, maxFill: .82 },
) {
  const rect = fitArtworkInCanonicalPlane(widthCm, heightCm, mountWidthPx, mountHeightPx, mode, homeFillRange)
  return {
    width: (rect.width / mountWidthPx) * 100,
    height: (rect.height / mountHeightPx) * 100,
  }
}

/**
 * Converts a framed artwork's physical dimensions into its share of a Scene's
 * measured wall plane. Small works stay small; only oversized mounts are
 * uniformly reduced so frame and artwork keep the same physical relationship.
 */
export function artworkPhysicalMountPercent(
  widthCm: number,
  heightCm: number,
  wallWidthCm: number,
  wallHeightCm: number,
  maxFill = .4,
  minimumEdgePx = 0,
) {
  const widthPercent = Math.max(.1, widthCm) / Math.max(.1, wallWidthCm) * 100
  const heightPercent = Math.max(.1, heightCm) / Math.max(.1, wallHeightCm) * 100
  const naturalWidthPx = widthPercent / 100 * 1672
  const naturalHeightPx = heightPercent / 100 * 941
  const minimumScale = minimumEdgePx > 0
    ? Math.max(1, minimumEdgePx / naturalWidthPx, minimumEdgePx / naturalHeightPx)
    : 1
  const fitScale = Math.min(minimumScale, maxFill * 100 / widthPercent, maxFill * 100 / heightPercent)
  return { width: widthPercent * fitScale, height: heightPercent * fitScale }
}

/** Fits the mounted artwork in canonical wall-plane coordinates before projection. */
export function fitArtworkInCanonicalPlane(
  widthCm: number,
  heightCm: number,
  planeWidthPx: number,
  planeHeightPx: number,
  mode: 'relative' | 'home' = 'relative',
  homeFillRange: { minFill: number; maxFill: number } = { minFill: .66, maxFill: .82 },
  center: { x: number; y: number } = { x: .5, y: .5 },
): PlaneRect {
  const safeWidth = Math.max(.1, widthCm)
  const safeHeight = Math.max(.1, heightCm)
  const source = mode === 'relative' ? artworkDisplaySize(safeWidth, safeHeight) : { width: safeWidth, height: safeHeight }
  const homeSize = Math.min(1, Math.max(0, (Math.max(safeWidth, safeHeight) - 35) / 125))
  const fillRatio = mode === 'relative' ? .86 : homeFillRange.minFill + homeSize * (homeFillRange.maxFill - homeFillRange.minFill)
  const scale = Math.min((planeWidthPx * fillRatio) / source.width, (planeHeightPx * fillRatio) / source.height, mode === 'relative' ? 1 : Number.POSITIVE_INFINITY)
  const width = source.width * scale
  const height = source.height * scale
  return {
    left: planeWidthPx * center.x - width / 2,
    top: planeHeightPx * center.y - height / 2,
    width,
    height,
  }
}
