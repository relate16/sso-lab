import type { Artwork } from '../types'

export const saleLabels = { NOT_FOR_SALE: '소장 작품', AVAILABLE: '구매 가능', RESERVED: '예약 중', SOLD: '판매 완료' }

export function primaryImage(artwork: Artwork) {
  return artwork.images.find((image) => image.primary) ?? artwork.images[0]
}

export function formatPrice(price: number | null) {
  return price === null ? '가격 문의' : `${new Intl.NumberFormat('ko-KR').format(price)}원`
}

export function ArtworkCard({ artwork, onInquiry, onView }: { artwork: Artwork; onInquiry: (artwork: Artwork) => void; onView: (artwork: Artwork) => void }) {
  const image = primaryImage(artwork)
  return <article className="artwork-card">
    <button className="artwork-card-image" type="button" onClick={() => onView(artwork)} aria-label={`${artwork.title} 크게 보기`}>
      <span className="artwork-card-image-stage">
        {image ? <img src={image.thumbnailUrl} alt={`${artwork.title} 작품`} loading="lazy" /> : <span>Image awaiting</span>}
      </span>
    </button>
    <div className="artwork-card-copy">
      <p className="artwork-state">{saleLabels[artwork.saleStatus]}</p>
      <h2>{artwork.title}</h2>
      <p>{[artwork.material, artwork.year, `${artwork.widthCm} × ${artwork.heightCm} cm`].filter(Boolean).join(' · ')}</p>
      <div><span>{formatPrice(artwork.price)}</span><span className="card-actions"><button className="text-button" type="button" onClick={() => onView(artwork)}>상세 보기</button><button className="text-button" type="button" onClick={() => onInquiry(artwork)}>문의</button></span></div>
    </div>
  </article>
}
