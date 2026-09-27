import type { Artwork } from '../types'
import { primaryImage } from './ArtworkCard'
import { artworkMountPercent } from './sceneState'

type ArtworkFrameProps = {
  artwork: Artwork
  mountWidthPx: number
  mountHeightPx: number
  variant?: 'detail' | 'home'
  priority?: boolean
  homeFillRange?: { minFill: number; maxFill: number }
  canonicalRect?: { left: number; top: number; width: number; height: number }
}

export function ArtworkFrame({ artwork, mountWidthPx, mountHeightPx, variant = 'detail', priority = false, homeFillRange, canonicalRect }: ArtworkFrameProps) {
  const image = primaryImage(artwork)
  const size = artworkMountPercent(artwork.widthCm, artwork.heightCm, mountWidthPx, mountHeightPx, variant === 'home' ? 'home' : 'relative', homeFillRange)
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
    <div className="mounted-artwork-media">
      {image ? <img src={variant === 'home' ? image.thumbnailUrl : image.webUrl} alt={`${artwork.title} 작품`} width={image.widthPx} height={image.heightPx} loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} /> : <span>Image awaiting</span>}
    </div>
  </div>
}
