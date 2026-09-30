import { FormEvent, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { studioApi } from '../api'
import { carouselCropStyle, createCarouselCropLayout, dragCarouselCrop, type CarouselCropLayout } from '../gallery/carouselCrop'
import type { Artwork, Csrf, FrameType, SaleStatus } from '../types'

const empty = {
  title: '', description: '', year: '', material: '', widthCm: '', heightCm: '', price: '',
  saleStatus: 'NOT_FOR_SALE' as SaleStatus, frameType: 'NONE' as FrameType,
  published: false, featured: false, carouselFocalX: '50', carouselFocalY: '50', carouselZoom: '1',
}

export function ArtworkEditor({ csrf }: { csrf: Csrf }) {
  const { id } = useParams()
  const navigate = useNavigate()
  const [form, setForm] = useState(empty)
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [pendingFiles, setPendingFiles] = useState<File[]>([])
  const [pendingPreviewUrl, setPendingPreviewUrl] = useState('')
  const [previewImageSize, setPreviewImageSize] = useState<{ width: number; height: number } | null>(null)
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [draggingCrop, setDraggingCrop] = useState(false)
  const cropDrag = useRef<{
    pointerId: number
    startX: number
    startY: number
    layout: CarouselCropLayout
  } | null>(null)

  useEffect(() => {
    if (!id) return
    void studioApi.artwork(id).then(item => {
      setArtwork(item)
      setForm({
        title: item.title,
        description: item.description,
        year: item.year?.toString() ?? '',
        material: item.material ?? '',
        widthCm: item.widthCm.toString(),
        heightCm: item.heightCm.toString(),
        price: item.price?.toString() ?? '',
        saleStatus: item.saleStatus,
        frameType: item.frameType,
        published: item.published,
        featured: item.featured,
        carouselFocalX: String(Math.round((Number.isFinite(item.carouselFocalX) ? item.carouselFocalX : .5) * 100)),
        carouselFocalY: String(Math.round((Number.isFinite(item.carouselFocalY) ? item.carouselFocalY : .5) * 100)),
        carouselZoom: (Number.isFinite(item.carouselZoom) ? item.carouselZoom : 1).toString(),
      })
    }).catch(() => setMessage('작품을 불러오지 못했습니다.'))
  }, [id])

  useEffect(() => {
    setPreviewImageSize(null)
    const file = pendingFiles[0]
    if (!file) {
      setPendingPreviewUrl('')
      return
    }
    const url = URL.createObjectURL(file)
    setPendingPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [pendingFiles])

  const set = (key: keyof typeof form, value: string | boolean) => {
    setForm(previous => ({ ...previous, [key]: value }))
  }
  const hasPrimaryImage = Boolean(artwork?.images.some(image => image.primary))
  const canPublish = hasPrimaryImage || pendingFiles.length > 0
  const primary = artwork?.images.find(image => image.primary) ?? artwork?.images[0]
  const carouselPreviewUrl = pendingPreviewUrl
    || primary?.webUrl.replace('/media/', '/studio/media/')
    || ''
  const previewWidth = previewImageSize?.width ?? primary?.widthPx ?? 16
  const previewHeight = previewImageSize?.height ?? primary?.heightPx ?? 9
  const carouselCrop = createCarouselCropLayout(
    previewWidth,
    previewHeight,
    Number(form.carouselFocalX) / 100,
    Number(form.carouselFocalY) / 100,
    Number(form.carouselZoom),
  )
  const cropStyle = carouselCropStyle(carouselCrop)
  const percent = (value: number) => String(Math.round(value * 10000) / 100)
  const focalXPercent = carouselCrop.focalX * 100
  const focalYPercent = carouselCrop.focalY * 100

  const body = (published = form.published) => ({
    ...form,
    published,
    year: form.year ? Number(form.year) : null,
    material: form.material || null,
    widthCm: Number(form.widthCm),
    heightCm: Number(form.heightCm),
    price: form.price ? Number(form.price) : null,
    carouselFocalX: carouselCrop.focalX,
    carouselFocalY: carouselCrop.focalY,
    carouselZoom: carouselCrop.zoom,
  })

  const startCarouselDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!carouselPreviewUrl) return
    cropDrag.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      layout: carouselCrop,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
    setDraggingCrop(true)
  }

  const moveCarouselCrop = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = cropDrag.current
    if (!drag || drag.pointerId !== event.pointerId) return
    const bounds = event.currentTarget.getBoundingClientRect()
    const next = dragCarouselCrop(
      drag.layout,
      (event.clientX - drag.startX) / bounds.width,
      (event.clientY - drag.startY) / bounds.height,
    )
    setForm(previous => ({
      ...previous,
      carouselFocalX: percent(next.focalX),
      carouselFocalY: percent(next.focalY),
    }))
  }

  const finishCarouselDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (cropDrag.current?.pointerId !== event.pointerId) return
    cropDrag.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    setDraggingCrop(false)
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!id && pendingFiles.length === 0) {
      setMessage('작품 이미지를 선택해주세요.')
      return
    }

    setMessage('')
    setSaving(true)
    let savedId = id
    try {
      const wantsPublish = form.published
      const mustUploadBeforePublishing = wantsPublish && !hasPrimaryImage && pendingFiles.length > 0
      let saved = id
        ? await studioApi.update(csrf, id, body(mustUploadBeforePublishing ? false : wantsPublish))
        : await studioApi.create(csrf, body(false))
      savedId = saved.id

      for (const file of pendingFiles) await studioApi.upload(csrf, saved.id, file)
      if (mustUploadBeforePublishing || (!id && wantsPublish)) {
        saved = await studioApi.update(csrf, saved.id, body(true))
      } else {
        saved = await studioApi.artwork(saved.id)
      }

      setArtwork(saved)
      setPendingFiles([])
      setMessage('작품과 이미지를 저장했습니다.')
      if (!id) navigate(`/studio/artworks/${saved.id}`, { replace: true })
    } catch (error) {
      if (!id && savedId) navigate(`/studio/artworks/${savedId}`, { replace: true })
      setMessage(error instanceof Error ? error.message : '저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  const refresh = async () => {
    if (id) setArtwork(await studioApi.artwork(id))
  }

  return <section className="studio-page editor-page">
    <header className="studio-page-header"><div><Link to="/studio/artworks">← 작품 목록</Link><h1>{id ? '작품 수정' : '새 작품'}</h1></div></header>
    {message && <p className="inline-notice" role="status">{message}</p>}
    <form className="artwork-form" onSubmit={submit}>
      <label>제목 *<input required maxLength={160} value={form.title} onChange={e => set('title', e.target.value)} /></label>
      <label className="full">설명<textarea maxLength={5000} rows={8} value={form.description} onChange={e => set('description', e.target.value)} /></label>
      <label>제작연도<input type="number" min="1000" max="9999" value={form.year} onChange={e => set('year', e.target.value)} /></label>
      <label>재료<input maxLength={240} value={form.material} onChange={e => set('material', e.target.value)} /></label>
      <label>가로(cm) *<input required type="number" min="0.01" step="0.01" value={form.widthCm} onChange={e => set('widthCm', e.target.value)} /></label>
      <label>세로(cm) *<input required type="number" min="0.01" step="0.01" value={form.heightCm} onChange={e => set('heightCm', e.target.value)} /></label>
      <label>가격<input type="number" min="0" step="1" value={form.price} onChange={e => set('price', e.target.value)} /></label>
      <label>판매 상태<select value={form.saleStatus} onChange={e => set('saleStatus', e.target.value)}><option value="NOT_FOR_SALE">비매품</option><option value="AVAILABLE">판매 가능</option><option value="RESERVED">예약중</option><option value="SOLD">판매 완료</option></select></label>
      <label>액자 타입<select value={form.frameType} onChange={e => set('frameType', e.target.value)}><option value="NONE">액자 없음</option><option value="MAT_BOARD">매트보드</option><option value="ACRYLIC_BOX">아크릴 관액자</option><option value="FLOATING_FRAME">띄움 액자</option></select></label>

      <label className="full artwork-image-field">
        작품 이미지 {!id && '*'}
        <span className="field-help">JPEG, PNG, WebP · 파일당 최대 12MB. 첫 번째 이미지가 대표 이미지가 됩니다.</span>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          required={!id}
          onChange={event => setPendingFiles(Array.from(event.target.files ?? []))}
        />
        {pendingFiles.length > 0 && <span className="selected-files">선택됨: {pendingFiles.map(file => file.name).join(', ')}</span>}
      </label>

      {artwork && artwork.images.length > 0 && <div className="full image-list" aria-label="등록된 작품 이미지">
        {artwork.images.map(image => <article key={image.id}>
          <img src={image.thumbnailUrl.replace('/media/', '/studio/media/')} alt="업로드된 작품" />
          <span>{image.primary ? '대표 이미지' : '추가 이미지'}</span>
          {!image.primary && <button type="button" onClick={() => void studioApi.primary(csrf, artwork.id, image.id).then(refresh)}>대표로 지정</button>}
          <button type="button" onClick={() => void studioApi.removeImage(csrf, artwork.id, image.id).then(refresh)}>삭제</button>
        </article>)}
      </div>}

      <fieldset className="full carousel-crop-editor">
        <legend>홈 캐러셀 대표 영역</legend>
        <p className="field-help">이미지를 잡아 끌어 원하는 부분을 가운데 십자선에 맞춘 뒤 확대 정도를 조정하세요.</p>
        <div
          className={`carousel-crop-preview${carouselPreviewUrl ? '' : ' empty'}${draggingCrop ? ' dragging' : ''}`}
          style={cropStyle}
          onPointerDown={startCarouselDrag}
          onPointerMove={moveCarouselCrop}
          onPointerUp={finishCarouselDrag}
          onPointerCancel={finishCarouselDrag}
        >
          {carouselPreviewUrl
            ? <><img
                src={carouselPreviewUrl}
                alt="캐러셀 잘림 영역 미리보기"
                draggable={false}
                onLoad={event => setPreviewImageSize({
                  width: event.currentTarget.naturalWidth,
                  height: event.currentTarget.naturalHeight,
                })}
              /><span className="carousel-focus-marker" aria-hidden="true" /></>
            : <span>작품 이미지를 선택하면 미리보기가 표시됩니다.</span>}
        </div>
        <div className="carousel-crop-controls">
          <label>가로 위치 <output>{Math.round(focalXPercent)}%</output><input type="range" min={carouselCrop.minFocalX * 100} max={carouselCrop.maxFocalX * 100} step="0.1" value={focalXPercent} disabled={carouselCrop.minFocalX === carouselCrop.maxFocalX} onChange={event => set('carouselFocalX', event.target.value)} /></label>
          <label>세로 위치 <output>{Math.round(focalYPercent)}%</output><input type="range" min={carouselCrop.minFocalY * 100} max={carouselCrop.maxFocalY * 100} step="0.1" value={focalYPercent} disabled={carouselCrop.minFocalY === carouselCrop.maxFocalY} onChange={event => set('carouselFocalY', event.target.value)} /></label>
          <label>확대 <output>{carouselCrop.zoom.toFixed(2)}×</output><input type="range" min="1" max="3" step="0.05" value={carouselCrop.zoom} onChange={event => {
            const next = createCarouselCropLayout(previewWidth, previewHeight, carouselCrop.focalX, carouselCrop.focalY, Number(event.target.value))
            setForm(previous => ({ ...previous, carouselFocalX: percent(next.focalX), carouselFocalY: percent(next.focalY), carouselZoom: event.target.value }))
          }} /></label>
        </div>
        <button className="crop-reset-button" type="button" onClick={() => setForm(previous => ({ ...previous, carouselFocalX: '50', carouselFocalY: '50', carouselZoom: '1' }))}>가운데로 초기화</button>
      </fieldset>

      <label className="check"><input type="checkbox" checked={form.featured} onChange={e => set('featured', e.target.checked)} /> 대표작</label>
      <label className="check"><input type="checkbox" checked={form.published} disabled={!canPublish} onChange={e => set('published', e.target.checked)} /> 공개 {!canPublish && <span className="check-help">(이미지 선택 후 가능)</span>}</label>
      <div className="full form-actions">
        <button className="primary-action" type="submit" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
        {id && <button className="danger-action" type="button" onClick={() => setConfirmDelete(true)}>작품 삭제</button>}
      </div>
    </form>

    {confirmDelete && <div className="dialog-backdrop" role="presentation"><div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-title"><h2 id="delete-title">작품을 삭제할까요?</h2><p>작품과 저장된 이미지가 함께 삭제됩니다.</p><div><button onClick={() => setConfirmDelete(false)}>취소</button><button className="danger-action" onClick={() => { if (id) void studioApi.remove(csrf, id).then(() => navigate('/studio/artworks')) }}>삭제</button></div></div></div>}
  </section>
}
