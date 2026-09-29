import type { FrameType } from '../types'

import acrylicBackLandscape from '../assets/frames/acrylic-box/acrylic-back-panel-landscape.png'
import acrylicBackPortrait from '../assets/frames/acrylic-box/acrylic-back-panel-portrait.png'
import acrylicFrontLandscape from '../assets/frames/acrylic-box/acrylic-front-case-landscape.png'
import acrylicFrontPortrait from '../assets/frames/acrylic-box/acrylic-front-case-portrait.png'
import acrylicShadowLandscape from '../assets/frames/acrylic-box/acrylic-shadow-landscape.png'
import acrylicShadowPortrait from '../assets/frames/acrylic-box/acrylic-shadow-portrait.png'
import floatingOuterLandscape from '../assets/frames/floating-frame/floating-frame-outer-landscape.png'
import floatingOuterPortrait from '../assets/frames/floating-frame/floating-frame-outer-portrait.png'
import floatingShadowLandscape from '../assets/frames/floating-frame/floating-frame-shadow-landscape.png'
import floatingShadowPortrait from '../assets/frames/floating-frame/floating-frame-shadow-portrait.png'
import matBoardLandscape from '../assets/frames/mat-board/mat-board-landscape.png'
import matBoardPortrait from '../assets/frames/mat-board/mat-board-portrait.png'
import matOuterLandscape from '../assets/frames/mat-board/mat-outer-frame-landscape.png'
import matOuterPortrait from '../assets/frames/mat-board/mat-outer-frame-portrait.png'

export type FrameOrientation = 'landscape' | 'portrait'

type FrameAsset = {
  src: string
  width: number
  height: number
}

type FrameRendererProps = {
  alt: string
  artworkHeight: number
  artworkSrc?: string
  artworkWidth: number
  frameType: FrameType
  priority?: boolean
}

const acrylicAssets: Record<FrameOrientation, { shadow: FrameAsset; back: FrameAsset; front: FrameAsset }> = {
  landscape: {
    shadow: { src: acrylicShadowLandscape, width: 1448, height: 1086 },
    back: { src: acrylicBackLandscape, width: 1448, height: 1086 },
    front: { src: acrylicFrontLandscape, width: 1454, height: 1082 },
  },
  portrait: {
    shadow: { src: acrylicShadowPortrait, width: 1086, height: 1448 },
    back: { src: acrylicBackPortrait, width: 1086, height: 1448 },
    front: { src: acrylicFrontPortrait, width: 1086, height: 1448 },
  },
}

const floatingAssets: Record<FrameOrientation, { shadow: FrameAsset; outer: FrameAsset }> = {
  landscape: {
    shadow: { src: floatingShadowLandscape, width: 1402, height: 1122 },
    outer: { src: floatingOuterLandscape, width: 1402, height: 1122 },
  },
  portrait: {
    shadow: { src: floatingShadowPortrait, width: 1122, height: 1402 },
    outer: { src: floatingOuterPortrait, width: 1122, height: 1402 },
  },
}

const matAssets: Record<FrameOrientation, { board: FrameAsset; outer: FrameAsset }> = {
  landscape: {
    board: { src: matBoardLandscape, width: 1448, height: 1086 },
    outer: { src: matOuterLandscape, width: 1448, height: 1086 },
  },
  portrait: {
    board: { src: matBoardPortrait, width: 1086, height: 1448 },
    outer: { src: matOuterPortrait, width: 1086, height: 1448 },
  },
}

export function frameOrientation(width: number, height: number): FrameOrientation {
  return width >= height ? 'landscape' : 'portrait'
}

function FrameLayer({ asset, role, priority }: { asset: FrameAsset; role: string; priority: boolean }) {
  return <img
    aria-hidden="true"
    className={`frame-composite__layer frame-composite__${role}`}
    decoding="async"
    fetchPriority="low"
    height={asset.height}
    loading={priority ? 'eager' : 'lazy'}
    src={asset.src}
    width={asset.width}
  />
}

function ArtworkLayer({ alt, priority, src }: { alt: string; priority: boolean; src?: string }) {
  return <div className="frame-composite__artwork">
    {src
      ? <img
          alt={alt}
          className="frame-composite__artwork-image"
          decoding="async"
          fetchPriority={priority ? 'high' : 'auto'}
          loading={priority ? 'eager' : 'lazy'}
          src={src}
        />
      : <span>Image awaiting</span>}
  </div>
}

export function FrameRenderer({ alt, artworkHeight, artworkSrc, artworkWidth, frameType, priority = false }: FrameRendererProps) {
  const orientation = frameOrientation(artworkWidth, artworkHeight)
  const artworkLayer = <ArtworkLayer alt={alt} priority={priority} src={artworkSrc} />

  if (frameType === 'ACRYLIC_BOX') {
    const assets = acrylicAssets[orientation]
    return <div className={`frame-composite frame-composite--acrylic-box frame-composite--${orientation}`} data-frame-type={frameType} data-orientation={orientation}>
      <FrameLayer asset={assets.shadow} role="shadow" priority={priority} />
      <FrameLayer asset={assets.back} role="back-panel" priority={priority} />
      {artworkLayer}
      <FrameLayer asset={assets.front} role="front-case" priority={priority} />
    </div>
  }

  if (frameType === 'FLOATING_FRAME') {
    const assets = floatingAssets[orientation]
    return <div className={`frame-composite frame-composite--floating-frame frame-composite--${orientation}`} data-frame-type={frameType} data-orientation={orientation}>
      <FrameLayer asset={assets.shadow} role="shadow" priority={priority} />
      {artworkLayer}
      <FrameLayer asset={assets.outer} role="outer-frame" priority={priority} />
    </div>
  }

  if (frameType === 'MAT_BOARD') {
    const assets = matAssets[orientation]
    return <div className={`frame-composite frame-composite--mat-board frame-composite--${orientation}`} data-frame-type={frameType} data-orientation={orientation}>
      {artworkLayer}
      <FrameLayer asset={assets.board} role="mat-board" priority={priority} />
      <FrameLayer asset={assets.outer} role="outer-frame" priority={priority} />
    </div>
  }

  return <div className={`frame-composite frame-composite--none frame-composite--${orientation}`} data-frame-type={frameType} data-orientation={orientation}>
    {artworkLayer}
  </div>
}
