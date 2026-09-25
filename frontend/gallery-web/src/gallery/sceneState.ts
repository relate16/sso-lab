export type SceneNumber = 1 | 2 | 3

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
  const minimumScale = 84 / largest
  const maximumScale = 500 / largest
  const scale = Math.min(maximumScale, Math.min(6, Math.max(3, minimumScale)))
  return { width: Math.round(safeWidth * scale), height: Math.round(safeHeight * scale) }
}
