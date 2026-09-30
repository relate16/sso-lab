import { useEffect, useState } from 'react'
import type { Artwork } from '../types'
import type { FrameType } from '../types'
import { primaryImage } from './ArtworkCard'
import { FrameRenderer } from './FrameRenderer'
import { artworkMountPercent, artworkPhysicalMountPercent } from './sceneState'

type ArtworkFrameProps = {
  artwork: Artwork
  mountWidthPx: number
  mountHeightPx: number
  variant?: 'detail' | 'home'
  priority?: boolean
  homeFillRange?: { minFill: number; maxFill: number }
  canonicalRect?: { left: number; top: number; width: number; height: number }
  physicalPlaneCm?: { width: number; height: number }
  zoomLevel?: number
  children?: React.ReactNode
}

type FrameOpening = { width: number; height: number }

const FRAME_OPENINGS: Record<'landscape' | 'portrait', Partial<Record<FrameType, FrameOpening>>> = {
  landscape: {
    // Measured transparent opening of the supplied 1448 × 1086 mat asset.
    MAT_BOARD: { width: 1052 / 1448, height: 529 / 1086 },
    // Raised canvas plane: it covers the deep inner bevel while leaving the
    // front wooden rail visible around the artwork.
    FLOATING_FRAME: { width: 1218 / 1402, height: 843 / 1122 },
  },
  portrait: {
    // Measured transparent opening of the supplied 1086 × 1448 mat asset.
    MAT_BOARD: { width: 684 / 1086, height: 964 / 1448 },
    FLOATING_FRAME: { width: 924 / 1122, height: 1204 / 1402 },
  },
}

/** Expands the mounted footprint so the frame opening, not its outer box, matches the artwork. */
export function framedMountDimensions(width: number, height: number, frameType: FrameType) {
  const orientation = width >= height ? 'landscape' : 'portrait'
  const opening = FRAME_OPENINGS[orientation][frameType] ?? { width: 1, height: 1 }
  return { width: width / opening.width, height: height / opening.height }
}

export function ArtworkFrame({ artwork, mountWidthPx, mountHeightPx, variant = 'detail', priority = false, homeFillRange, canonicalRect, physicalPlaneCm, zoomLevel = 1, children }: ArtworkFrameProps) {
  const image = primaryImage(artwork)
  const [highResolutionImageId, setHighResolutionImageId] = useState<string | null>(null)
  useEffect(() => {
    if (variant !== 'detail' || zoomLevel < 4 || !image?.originalUrl || highResolutionImageId === image.id) return
    let active = true
    const preload = new Image()
    preload.onload = () => { if (active) setHighResolutionImageId(image.id) }
    preload.src = image.originalUrl
    return () => { active = false }
  }, [highResolutionImageId, image?.id, image?.originalUrl, variant, zoomLevel])
  const footprint = framedMountDimensions(artwork.widthCm, artwork.heightCm, artwork.frameType)
  const size = physicalPlaneCm
    ? artworkPhysicalMountPercent(footprint.width, footprint.height, physicalPlaneCm.width, physicalPlaneCm.height, .9, mountWidthPx, mountHeightPx)
    : artworkMountPercent(footprint.width, footprint.height, mountWidthPx, mountHeightPx, variant === 'home' ? 'home' : 'relative', homeFillRange)
  const style = canonicalRect ? {
    '--mounted-left': `${canonicalRect.left / mountWidthPx * 100}%`,
    '--mounted-top': `${canonicalRect.top / mountHeightPx * 100}%`,
    '--mounted-width': `${canonicalRect.width / mountWidthPx * 100}%`,
    '--mounted-height': `${canonicalRect.height / mountHeightPx * 100}%`,
  } : { '--mounted-width': `${size.width}%`, '--mounted-height': `${size.height}%` }
  return <div
    className={`mounted-artwork mounted-artwork-${variant} frame-${artwork.frameType.toLowerCase()}`}
    style={style as React.CSSProperties}
  >
    <FrameRenderer
      alt={`${artwork.title} 작품`}
      artworkHeight={image?.heightPx ?? artwork.heightCm}
      artworkSrc={image ? (variant === 'home' ? image.thumbnailUrl : highResolutionImageId === image.id ? image.originalUrl : image.webUrl) : undefined}
      artworkWidth={image?.widthPx ?? artwork.widthCm}
      frameType={artwork.frameType}
      priority={priority}
    />
    {children}
  </div>
}
