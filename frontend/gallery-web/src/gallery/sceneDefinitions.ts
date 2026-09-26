import type { SceneNumber } from './sceneState'

export type SceneDefinition = {
  id: SceneNumber
  name: string
  className: string
  spatialDescription: string
  asset: string
  mobileAsset: string
  mount: ScenePlacement
  caption: { left: number; top: number }
  focalPoint: { x: number; y: number }
}

export type ScenePlacement = {
  left: number
  top: number
  width: number
  height: number
}

export type HomeSlot = ScenePlacement & {
  id: 'leftWall' | 'centerLeft' | 'centerRight' | 'rightWall'
  transform: string
  shadow: string
}

export const SCENE_REFERENCE_SIZE = { width: 1672, height: 941 } as const

export const HOME_SCENE = {
  asset: '/images/gallery/gallery-home-scene.webp',
  mobileAsset: '/images/gallery/gallery-home-scene-mobile.webp',
  focalPoint: { x: 50, y: 46 },
} as const

// Percentages are normalized from the approved 1672 x 941 Home asset. Keeping
// them here makes the image the single coordinate system for every artwork.
export const HOME_SLOTS: HomeSlot[] = [
  { id: 'leftWall', left: 8.7, top: 26.1, width: 10.8, height: 33.1, transform: 'perspective(900px) rotateY(12deg) rotateZ(.3deg)', shadow: '12px 14px 18px rgba(65, 45, 30, .25)' },
  { id: 'centerLeft', left: 37.9, top: 32.4, width: 16.6, height: 24.3, transform: 'none', shadow: '8px 13px 16px rgba(65, 45, 30, .22)' },
  { id: 'centerRight', left: 61.7, top: 31.4, width: 11.9, height: 26.1, transform: 'none', shadow: '8px 13px 16px rgba(65, 45, 30, .22)' },
  { id: 'rightWall', left: 81.4, top: 17.7, width: 9.4, height: 45.4, transform: 'perspective(900px) rotateY(-13deg) rotateZ(-.2deg)', shadow: '-11px 15px 18px rgba(65, 45, 30, .27)' },
]

export const SCENE_DEFINITIONS: Record<SceneNumber, SceneDefinition> = {
  1: {
    id: 1,
    name: 'Central hall',
    className: 'scene-1',
    spatialDescription: '기둥 사이의 넓은 중앙 전시실',
    asset: '/images/gallery/gallery-detail-scene-1.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-1-mobile.webp',
    mount: { left: 34.2, top: 25.9, width: 31.5, height: 35.5 },
    caption: { left: 68.2, top: 43 },
    focalPoint: { x: 50, y: 43.5 },
  },
  2: {
    id: 2,
    name: 'Quiet room',
    className: 'scene-2',
    spatialDescription: '두 기둥 안쪽의 고요한 전시실',
    asset: '/images/gallery/gallery-detail-scene-2.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-2-mobile.webp',
    mount: { left: 35.2, top: 26.5, width: 29.6, height: 32.9 },
    caption: { left: 67.3, top: 43 },
    focalPoint: { x: 50, y: 42.8 },
  },
  3: {
    id: 3,
    name: 'Inner gallery',
    className: 'scene-3',
    spatialDescription: '깊은 프레임 안쪽의 집중된 전시실',
    asset: '/images/gallery/gallery-detail-scene-3.webp',
    mobileAsset: '/images/gallery/gallery-detail-scene-3-mobile.webp',
    mount: { left: 34.2, top: 24.8, width: 31.4, height: 33.9 },
    caption: { left: 68.2, top: 42 },
    focalPoint: { x: 50, y: 42 },
  },
}
