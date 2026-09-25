import { createServer } from 'node:http'

const ids = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333', '44444444-4444-4444-8444-444444444444']
const frames = ['FLOATING_FRAME', 'MAT_BOARD', 'ACRYLIC_BOX', 'NONE']
const palettes = [['#2e4053', '#c4d6d1'], ['#80695a', '#e6d8c4'], ['#415b55', '#b8c5bc'], ['#4e4b58', '#d8c6ce']]
const artworks = ids.map((id, index) => ({
  id, title: ['Winter Window', 'Snow Field No. 3', 'Remaining Light', 'Silent Garden'][index],
  description: '차가운 계절의 공기와 늦은 오후의 빛이 천천히 포개지는 순간을 기록한 작품입니다. 화면의 고요한 층위를 따라 오래 머물러 보세요.',
  year: 2024 + index % 2, material: 'Oil on canvas', widthCm: [48, 90, 120, 32][index], heightCm: [72, 60, 150, 32][index],
  price: 1200000 + index * 450000, saleStatus: index === 2 ? 'SOLD' : 'AVAILABLE', frameType: frames[index],
  published: true, featured: index < 2, displayOrder: index, publishedAt: new Date(2026, 8, 20 - index).toISOString(),
  images: [{ id, storageKey: `preview-${index}`, webUrl: `/api/v1/gallery/media/preview-${index}/web`, thumbnailUrl: `/api/v1/gallery/media/preview-${index}/thumbnail`, widthPx: 900, heightPx: 1200, sortOrder: 0, primary: true }],
}))

function artworkSvg(index) {
  const [dark, light] = palettes[index]
  return `<svg xmlns="http://www.w3.org/2000/svg" width="900" height="1200" viewBox="0 0 900 1200"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="${light}"/><stop offset="1" stop-color="#f2eee5"/></linearGradient></defs><rect width="900" height="1200" fill="url(#g)"/><circle cx="${250 + index * 90}" cy="${310 + index * 70}" r="210" fill="${dark}" opacity=".72"/><path d="M0 820 Q220 ${650 + index * 30} 470 820 T900 760 V1200 H0Z" fill="${dark}" opacity=".84"/><path d="M80 940 Q380 720 820 910" fill="none" stroke="#f5f0e4" stroke-width="18" opacity=".7"/></svg>`
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', 'http://127.0.0.1')
  const media = url.pathname.match(/\/media\/preview-(\d)\/(?:web|thumbnail)$/)
  if (media) { response.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-store' }); response.end(artworkSvg(Number(media[1]))); return }
  if (url.pathname === '/api/v1/gallery/home') return json(response, artworks)
  if (url.pathname === '/api/v1/gallery/artworks') return json(response, { content: artworks, page: 0, size: 60, totalElements: artworks.length, totalPages: 1 })
  const detail = url.pathname.match(/\/api\/v1\/gallery\/artworks\/(.+)$/)
  if (detail) { const artwork = artworks.find((item) => item.id === detail[1]); return artwork ? json(response, artwork) : json(response, { error: 'not_found' }, 404) }
  json(response, { error: 'not_found' }, 404)
}).listen(18084, '127.0.0.1', () => console.log('Gallery preview fixture listening on 18084'))

function json(response, body, status = 200) { response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); response.end(JSON.stringify(body)) }
