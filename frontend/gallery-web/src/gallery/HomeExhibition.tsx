import { Link } from 'react-router-dom'
import type { Artwork } from '../types'
import { ArtworkFrame } from './ArtworkFrame'
import { HOME_SCENE, HOME_SLOTS, SCENE_REFERENCE_SIZE } from './sceneDefinitions'

export function HomeExhibition({ artworks }: { artworks: Artwork[] }) {
  return <div className="home-scene-shell">
    <div className="home-scene-scroll" aria-label="최근 공개 작품이 걸린 전시장 전경">
      <div className="home-scene-canvas">
      <picture className="scene-background" aria-hidden="true">
        <source media="(max-width: 700px)" srcSet={HOME_SCENE.mobileAsset} />
        <img src={HOME_SCENE.asset} alt="" width="1672" height="941" fetchPriority="high" />
      </picture>
      {artworks.slice(0, 4).map((artwork, index) => {
        const slot = HOME_SLOTS[index]
        const style = {
          '--slot-left': `${slot.left}%`,
          '--slot-top': `${slot.top}%`,
          '--slot-width': `${slot.width}%`,
          '--slot-height': `${slot.height}%`,
          '--slot-transform': slot.transform,
          '--slot-shadow': slot.shadow,
        } as React.CSSProperties
        return <Link
          className={`home-artwork-slot home-artwork-slot-${slot.id}`}
          key={artwork.id}
          style={style}
          to={`/artworks/${artwork.id}?scene=1`}
          aria-label={`${artwork.title} 작품 전시장 보기`}
          title={artwork.title}
        >
          <ArtworkFrame
            artwork={artwork}
            mountWidthPx={SCENE_REFERENCE_SIZE.width * slot.width / 100}
            mountHeightPx={SCENE_REFERENCE_SIZE.height * slot.height / 100}
            variant="home"
            priority={index < 2}
          />
        </Link>
      })}
      </div>
    </div>
    <span className="home-scene-pan-hint" aria-hidden="true">← 전시장을 좌우로 살펴보세요 →</span>
  </div>
}
