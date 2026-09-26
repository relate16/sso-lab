import type { Artwork } from '../types'
import { primaryImage } from './ArtworkCard'
import { artworkMountPercent } from './sceneState'

type ArtworkFrameProps = {
  artwork: Artwork
  mountWidthPx: number
  mountHeightPx: number
  variant?: 'detail' | 'home'
  priority?: boolean
}

export function ArtworkFrame({ artwork, mountWidthPx, mountHeightPx, variant = 'detail', priority = false }: ArtworkFrameProps) {
  const image = primaryImage(artwork)
  const size = artworkMountPercent(artwork.widthCm, artwork.heightCm, mountWidthPx, mountHeightPx, variant === 'home' ? 'fill' : 'relative')
  return <div
    className={`mounted-artwork mounted-artwork-${variant} frame-${artwork.frameType.toLowerCase()}`}
    style={{ '--mounted-width': `${size.width}%`, '--mounted-height': `${size.height}%` } as React.CSSProperties}
  >
    <div className="mounted-artwork-media">
      {image ? <img src={variant === 'home' ? image.thumbnailUrl : image.webUrl} alt={`${artwork.title} 작품`} width={image.widthPx} height={image.heightPx} loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} /> : <span>Image awaiting</span>}
    </div>
  </div>
}
