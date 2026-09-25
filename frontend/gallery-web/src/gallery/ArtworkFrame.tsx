import type { Artwork } from '../types'
import { primaryImage } from './ArtworkCard'
import { artworkDisplaySize } from './sceneState'

export function ArtworkFrame({ artwork }: { artwork: Artwork }) {
  const image = primaryImage(artwork)
  const size = artworkDisplaySize(artwork.widthCm, artwork.heightCm)
  return <div className={`scene-artwork frame-${artwork.frameType.toLowerCase()}`} style={{ '--art-width': `${size.width}px`, '--art-height': `${size.height}px` } as React.CSSProperties}>
    {image ? <img src={image.webUrl} alt={`${artwork.title} 작품`} width={image.widthPx} height={image.heightPx} fetchPriority="high" /> : <span>Image awaiting</span>}
  </div>
}
