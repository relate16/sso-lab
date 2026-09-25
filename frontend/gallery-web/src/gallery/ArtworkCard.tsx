import type { Artwork } from '../types'

const saleLabels = { NOT_FOR_SALE: '소장 작품', AVAILABLE: '구매 가능', RESERVED: '예약 중', SOLD: '판매 완료' }

export function primaryImage(artwork: Artwork) {
  return artwork.images.find((image) => image.primary) ?? artwork.images[0]
}

export function formatPrice(price: number | null) {
  return price === null ? '가격 문의' : `${new Intl.NumberFormat('ko-KR').format(price)}원`
}

export function ArtworkCard({ artwork, onInquiry }: { artwork: Artwork; onInquiry: (artwork: Artwork) => void }) {
  const image = primaryImage(artwork)
  return <article className="artwork-card">
    <div className="artwork-card-image">
      {image ? <img src={image.thumbnailUrl} alt={`${artwork.title} 작품`} loading="lazy" /> : <span>Image awaiting</span>}
    </div>
    <div className="artwork-card-copy">
      <p className="artwork-state">{saleLabels[artwork.saleStatus]}</p>
      <h2>{artwork.title}</h2>
      <p>{[artwork.material, artwork.year, `${artwork.widthCm} × ${artwork.heightCm} cm`].filter(Boolean).join(' · ')}</p>
      <div><span>{formatPrice(artwork.price)}</span><button className="text-button" type="button" onClick={() => onInquiry(artwork)}>작품 문의</button></div>
    </div>
  </article>
}
