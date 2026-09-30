import type { FrameType } from '../types'

export type FrameOrientation = 'landscape' | 'portrait'
export type FrameVisibleBounds = { left: number; top: number; right: number; bottom: number }

/** Physical defaults for the actual frame products represented by the supplied assets. */
export const FRAME_PHYSICAL_SPECS: Record<FrameType, {
  depthCm: number
  visibleBounds: Record<FrameOrientation, FrameVisibleBounds>
}> = {
  NONE: {
    depthCm: 0,
    visibleBounds: {
      landscape: { left: 0, top: 0, right: 1, bottom: 1 },
      portrait: { left: 0, top: 0, right: 1, bottom: 1 },
    },
  },
  ACRYLIC_BOX: {
    depthCm: 5,
    visibleBounds: {
      landscape: { left: 56 / 1454, top: 100 / 1082, right: 1398 / 1454, bottom: 985 / 1082 },
      portrait: { left: 53 / 1086, top: 68 / 1448, right: 1034 / 1086, bottom: 1378 / 1448 },
    },
  },
  FLOATING_FRAME: {
    depthCm: 4,
    visibleBounds: {
      landscape: { left: 47 / 1402, top: 100 / 1122, right: 1356 / 1402, bottom: 1020 / 1122 },
      portrait: { left: 60 / 1122, top: 61 / 1402, right: 1064 / 1122, bottom: 1342 / 1402 },
    },
  },
  MAT_BOARD: {
    depthCm: 3,
    visibleBounds: {
      landscape: { left: 54 / 1448, top: 132 / 1086, right: 1393 / 1448, bottom: 954 / 1086 },
      portrait: { left: 54 / 1086, top: 85 / 1448, right: 1032 / 1086, bottom: 1363 / 1448 },
    },
  },
}

export function frameDepthCm(frameType: FrameType) {
  return FRAME_PHYSICAL_SPECS[frameType].depthCm
}

export function frameVisibleBounds(frameType: FrameType, orientation: FrameOrientation) {
  return FRAME_PHYSICAL_SPECS[frameType].visibleBounds[orientation]
}
