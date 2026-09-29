import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'

const ids = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333', '44444444-4444-4444-8444-444444444444', '55555555-5555-4555-8555-555555555555', '66666666-6666-4666-8666-666666666666', '77777777-7777-4777-8777-777777777777', '88888888-8888-4888-8888-888888888888', '99999999-9999-4999-8999-999999999999', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa']
const frames = ['FLOATING_FRAME', 'MAT_BOARD', 'ACRYLIC_BOX', 'NONE', 'ACRYLIC_BOX', 'ACRYLIC_BOX', 'MAT_BOARD', 'MAT_BOARD', 'FLOATING_FRAME', 'FLOATING_FRAME']
const palettes = [['#2e4053', '#c4d6d1'], ['#80695a', '#e6d8c4'], ['#415b55', '#b8c5bc'], ['#4e4b58', '#d8c6ce'], ['#384b62', '#c8d2dc'], ['#5b5752', '#d8d4cc'], ['#384b62', '#c8d2dc'], ['#5b5752', '#d8d4cc'], ['#384b62', '#c8d2dc'], ['#5b5752', '#d8d4cc']]
const imageSizes = [[900, 1200], [1200, 800], [900, 1200], [1000, 1000], [706, 353], [2849, 4021], [706, 353], [2849, 4021], [706, 353], [2849, 4021]]
const landscapeExample = readFileSync(new URL('./fixtures/acrylic-landscape-example.png', import.meta.url))
const portraitExample = readFileSync(new URL('./fixtures/acrylic-portrait-example.jpg', import.meta.url))
const artworks = ids.map((id, index) => ({
  id, title: ['Winter Window', 'Snow Field No. 3', 'Remaining Light', 'Silent Garden', 'Landscape Study', 'Portrait Study', 'Landscape · Mat Board', 'Portrait · Mat Board', 'Landscape · Floater Frame', 'Portrait · Floater Frame'][index],
  description: '차가운 계절의 공기와 늦은 오후의 빛이 천천히 포개지는 순간을 기록한 작품입니다. 화면의 고요한 층위를 따라 오래 머물러 보세요.',
  year: 2024 + index % 2, material: index >= 4 ? 'Pencil on paper' : 'Oil on canvas', widthCm: [48, 90, 120, 32, 100, 70, 100, 70, 100, 70][index], heightCm: [72, 60, 150, 32, 50, 99, 50, 99, 50, 99][index],
  price: 1200000 + index * 450000, saleStatus: index === 2 ? 'SOLD' : 'AVAILABLE', frameType: frames[index],
  published: true, featured: index < 2, displayOrder: index, publishedAt: new Date(2026, 8, 20 - index).toISOString(),
  images: [{ id, storageKey: `preview-${index}`, originalUrl: `/api/v1/gallery/media/preview-${index}/original`, webUrl: `/api/v1/gallery/media/preview-${index}/web`, thumbnailUrl: `/api/v1/gallery/media/preview-${index}/thumbnail`, widthPx: imageSizes[index][0], heightPx: imageSizes[index][1], sortOrder: 0, primary: true }],
}))
const homeArtworks = [artworks[4], artworks[5], artworks[0], artworks[1]]

function artworkSvg(index) {
  const [dark, light] = palettes[index]
  const [width, height] = imageSizes[index]
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 900 1200" preserveAspectRatio="none"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="${light}"/><stop offset="1" stop-color="#f2eee5"/></linearGradient></defs><rect width="900" height="1200" fill="url(#g)"/><circle cx="${250 + index * 90}" cy="${310 + index * 70}" r="210" fill="${dark}" opacity=".72"/><path d="M0 820 Q220 ${650 + index * 30} 470 820 T900 760 V1200 H0Z" fill="${dark}" opacity=".84"/><path d="M80 940 Q380 720 820 910" fill="none" stroke="#f5f0e4" stroke-width="18" opacity=".7"/></svg>`
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', 'http://127.0.0.1')
  const media = url.pathname.match(/\/media\/preview-(\d)\/(?:original|web|thumbnail)$/)
  if (media && ['4', '6', '8'].includes(media[1])) { response.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' }); response.end(landscapeExample); return }
  if (media && ['5', '7', '9'].includes(media[1])) { response.writeHead(200, { 'Content-Type': 'image/jpeg', 'Cache-Control': 'no-store' }); response.end(portraitExample); return }
  if (media) { response.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-store' }); response.end(artworkSvg(Number(media[1]))); return }
  if (url.pathname === '/api/v1/gallery/home') return json(response, homeArtworks)
  if (url.pathname === '/api/v1/gallery/artworks') return json(response, { content: artworks, page: 0, size: 60, totalElements: artworks.length, totalPages: 1 })
  const detail = url.pathname.match(/\/api\/v1\/gallery\/artworks\/(.+)$/)
  if (detail) { const artwork = artworks.find((item) => item.id === detail[1]); return artwork ? json(response, artwork) : json(response, { error: 'not_found' }, 404) }
  json(response, { error: 'not_found' }, 404)
}).listen(18084, '127.0.0.1', () => console.log('Gallery preview fixture listening on 18084'))

function json(response, body, status = 200) { response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); response.end(JSON.stringify(body)) }
