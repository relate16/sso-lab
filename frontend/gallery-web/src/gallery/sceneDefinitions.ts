import type { NormalizedCenter, NormalizedPoint, NormalizedQuad, SceneNumber } from './sceneState'

export type SceneDefinition = {
  id: SceneNumber
  name: string
  className: string
  spatialDescription: string
  asset: string
  mobileAsset: string
  mount: ScenePlacement
  wallWidthCm: number
  caption: { left: number; top: number }
  zoomControl: { left: number; top: number }
  focalPoint: { x: number; y: number }
  artworkShadow: string
}

export type ScenePlacement = {
  centerX: number
  centerY: number
  width: number
  height: number
}

type PixelPoint = readonly [x: number, y: number]

export type HomeSlot = {
  id: 'leftWall' | 'centerLeft' | 'centerRight' | 'rightWall'
  cornersPx: {
    topLeft: PixelPoint
    topRight: PixelPoint
    bottomRight: PixelPoint
    bottomLeft: PixelPoint
  }
  corners: NormalizedQuad
  mountCenterPx: PixelPoint
  mountCenter: NormalizedCenter
  lightCenterPx: PixelPoint
  lightCenter: NormalizedCenter
  horizontalVanishingPointPx?: PixelPoint
  horizontalVanishingPoint?: NormalizedCenter
  sizeClamp: { minFill: number; maxFill: number }
  artworkCenter?: { x: number; y: number }
  shadowPreset: 'leftWall' | 'backWall' | 'rightWall'
  lightPreset: 'leftWall' | 'backWall' | 'rightWall'
}

export const SCENE_REFERENCE_SIZE = { width: 1672, height: 941 } as const

export const HOME_SCENE = {
  asset: '/images/gallery/gallery-home-scene.webp',
  mobileAsset: '/images/gallery/gallery-home-scene-mobile.webp',
  focalPoint: { x: 50, y: 46 },
} as const

export const HOME_SHADOW_PRESETS = {
  leftWall: '10px 12px 18px rgba(57, 39, 25, .22)',
  backWall: '6px 12px 18px rgba(54, 37, 25, .18)',
  rightWall: '-10px 13px 19px rgba(57, 39, 25, .22)',
} as const

export const HOME_LIGHT_PRESETS = {
  leftWall: 'radial-gradient(circle at var(--slot-light-x) var(--slot-light-y), rgba(255, 244, 214, .2), rgba(255, 244, 214, .055) 48%, rgba(82, 57, 38, .045) 100%)',
  backWall: 'radial-gradient(circle at var(--slot-light-x) var(--slot-light-y), rgba(255, 247, 224, .17), rgba(255, 247, 224, .045) 52%, rgba(90, 62, 41, .035) 100%)',
  rightWall: 'radial-gradient(circle at var(--slot-light-x) var(--slot-light-y), rgba(255, 244, 214, .2), rgba(255, 244, 214, .055) 48%, rgba(82, 57, 38, .045) 100%)',
} as const

type HomeSlotSource = Omit<HomeSlot, 'corners' | 'mountCenter' | 'lightCenter' | 'horizontalVanishingPoint'>

function normalizedPoint([x, y]: PixelPoint): NormalizedPoint {
  return [x / SCENE_REFERENCE_SIZE.width, y / SCENE_REFERENCE_SIZE.height]
}

function homeSlot(source: HomeSlotSource): HomeSlot {
  return {
    ...source,
    corners: {
      topLeft: normalizedPoint(source.cornersPx.topLeft),
      topRight: normalizedPoint(source.cornersPx.topRight),
      bottomRight: normalizedPoint(source.cornersPx.bottomRight),
      bottomLeft: normalizedPoint(source.cornersPx.bottomLeft),
    },
    mountCenter: { x: normalizedPoint(source.mountCenterPx)[0], y: normalizedPoint(source.mountCenterPx)[1] },
    lightCenter: { x: normalizedPoint(source.lightCenterPx)[0], y: normalizedPoint(source.lightCenterPx)[1] },
    horizontalVanishingPoint: source.horizontalVanishingPointPx
      ? { x: normalizedPoint(source.horizontalVanishingPointPx)[0], y: normalizedPoint(source.horizontalVanishingPointPx)[1] }
      : undefined,
  }
}

// Every coordinate is normalized against the approved 1672 x 941 Home asset.
// The four corners describe the actual wall plane. Artwork, frame and shadow
// are composed first, then projected onto this plane as one mounted object.
export const HOME_SLOTS: HomeSlot[] = [
  homeSlot({
    id: 'leftWall',
    cornersPx: { topLeft: [151, 248], topRight: [322, 278.7744], bottomRight: [321, 540.6849], bottomLeft: [154, 552] },
    mountCenterPx: [237, 405.75],
    lightCenterPx: [247, 297],
    horizontalVanishingPointPx: [1379, 469],
    sizeClamp: { minFill: .64, maxFill: .78 },
    artworkCenter: { x: .5, y: .6 },
    shadowPreset: 'leftWall', lightPreset: 'leftWall',
  }),
  homeSlot({
    id: 'centerLeft',
    cornersPx: { topLeft: [638, 317], topRight: [910, 305], bottomRight: [907, 531], bottomLeft: [637, 527] },
    mountCenterPx: [773, 420],
    lightCenterPx: [762, 323],
    sizeClamp: { minFill: .65, maxFill: .8 },
    shadowPreset: 'backWall', lightPreset: 'backWall',
  }),
  homeSlot({
    id: 'centerRight',
    cornersPx: { topLeft: [1032, 303], topRight: [1226, 292], bottomRight: [1223, 537], bottomLeft: [1036, 533] },
    mountCenterPx: [1129.25, 416.25],
    lightCenterPx: [1127, 309],
    sizeClamp: { minFill: .65, maxFill: .8 },
    shadowPreset: 'backWall', lightPreset: 'backWall',
  }),
  homeSlot({
    id: 'rightWall',
    cornersPx: { topLeft: [1363, 225], topRight: [1509, 169.6521], bottomRight: [1506, 590.5759], bottomLeft: [1368, 569] },
    mountCenterPx: [1436.5, 388.25],
    lightCenterPx: [1431, 240],
    horizontalVanishingPointPx: [722, 468],
    sizeClamp: { minFill: .8, maxFill: .86 },
    artworkCenter: { x: .5, y: .62 },
    shadowPreset: 'rightWall', lightPreset: 'rightWall',
  }),
]

export const SCENE_DEFINITIONS: Record<SceneNumber, SceneDefinition> = {
  1: {
    id: 1,
    name: 'Central hall',
    className: 'scene-1',
    spatialDescription: '기둥 사이의 넓은 중앙 전시실',
    asset: '/images/gallery/gallery-detail-scene-1.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-1-mobile.webp',
    mount: { centerX: 49.95, centerY: 43.65, width: 31.5, height: 35.5 },
    wallWidthCm: 860,
    caption: { left: 68.2, top: 43 },
    zoomControl: { left: 66.45, top: 43.7 },
    focalPoint: { x: 50, y: 43.5 },
    artworkShadow: '9px 17px 25px rgba(48, 32, 21, .21)',
  },
  2: {
    id: 2,
    name: 'Quiet room',
    className: 'scene-2',
    spatialDescription: '두 기둥 안쪽의 고요한 전시실',
    asset: '/images/gallery/gallery-detail-scene-2.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-2-mobile.webp',
    mount: { centerX: 50, centerY: 42.95, width: 29.6, height: 32.9 },
    wallWidthCm: 800,
    caption: { left: 67.3, top: 43 },
    zoomControl: { left: 65.55, top: 43.2 },
    focalPoint: { x: 50, y: 42.8 },
    artworkShadow: '7px 16px 24px rgba(48, 32, 21, .19)',
  },
  3: {
    id: 3,
    name: 'Inner gallery',
    className: 'scene-3',
    spatialDescription: '깊은 프레임 안쪽의 집중된 전시실',
    asset: '/images/gallery/gallery-detail-scene-3.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-3-mobile.webp',
    mount: { centerX: 49.9, centerY: 41.75, width: 31.4, height: 33.9 },
    wallWidthCm: 760,
    caption: { left: 68.2, top: 42 },
    zoomControl: { left: 66.35, top: 42.4 },
    focalPoint: { x: 50, y: 42 },
    artworkShadow: '9px 18px 26px rgba(48, 32, 21, .22)',
  },
}
