import type { SceneNumber } from './sceneState'

export type SceneDefinition = {
  id: SceneNumber
  name: string
  className: string
  spatialDescription: string
}

export const SCENE_DEFINITIONS: Record<SceneNumber, SceneDefinition> = {
  1: { id: 1, name: 'Front wall', className: 'scene-1', spatialDescription: '정면 벽과 얕은 바닥 원근' },
  2: { id: 2, name: 'Corner room', className: 'scene-2', spatialDescription: '두 벽이 만나는 밝은 코너 공간' },
  3: { id: 3, name: 'Long gallery', className: 'scene-3', spatialDescription: '측면 벽이 소실점으로 이어지는 긴 전시 공간' },
}
