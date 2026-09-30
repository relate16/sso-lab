import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import type { Artwork } from '../types'
import { ArtworkFrame, framedMountDimensions } from './ArtworkFrame'
import { frameDepthCm, frameVisibleBounds, type FrameOrientation } from './frameGeometry'
import { EXHIBITION_WALL_SIZE_CM, HOME_LIGHT_PRESETS, HOME_SCENE, HOME_SHADOW_PRESETS, HOME_SLOTS, SCENE_REFERENCE_SIZE, type HomeSlot } from './sceneDefinitions'
import {
  fitPhysicalArtworkInWallRegion,
  homographyToMatrix3d,
  normalizedQuadPlacement,
  projectHomographyPoint,
  rectangleToQuadHomography,
  type NormalizedCenter,
  type NormalizedPoint,
  type NormalizedQuad,
} from './sceneState'

type CornerName = keyof NormalizedQuad
type CornerErrors = Record<CornerName, number>

type ImageMapping = {
  naturalWidth: number
  naturalHeight: number
  renderedWidth: number
  renderedHeight: number
  cropOffsetX: number
  cropOffsetY: number
  scaleX: number
  scaleY: number
  objectFit: string
  objectPosition: string
}

type DebugProjection = {
  actual: NormalizedQuad
  canonicalActual: { left: number; top: number; width: number; height: number }
  canonicalExpected: { left: number; top: number; width: number; height: number }
  expected: NormalizedQuad
  errors: CornerErrors
  homography: string
  horizontalVanishingErrorPx: number | null
  maxErrorPx: number
}

type FrameProjection = {
  aspectRatio: number
  quad: NormalizedQuad
}

type MappedSlot = {
  mountCenter: NormalizedCenter
  lightCenter: NormalizedCenter
  horizontalVanishingPoint?: NormalizedCenter
  target: NormalizedQuad
}

type BrowserQuad = { p1: DOMPoint; p2: DOMPoint; p3: DOMPoint; p4: DOMPoint }
type QuadElement = HTMLElement & { getBoxQuads?: () => BrowserQuad[] }

const CORNERS: CornerName[] = ['topLeft', 'topRight', 'bottomRight', 'bottomLeft']
const HOME_FRAME_DEPTH_COLORS: Record<Artwork['frameType'], { front: string; wall: string; opacity: number }> = {
  NONE: { front: 'transparent', wall: 'transparent', opacity: 0 },
  MAT_BOARD: { front: '#262421', wall: '#4c4842', opacity: .96 },
  ACRYLIC_BOX: { front: '#dedbd2', wall: '#9b9992', opacity: .62 },
  FLOATING_FRAME: { front: '#533928', wall: '#78543d', opacity: .96 },
}
const DEFAULT_IMAGE_MAPPING: ImageMapping = {
  naturalWidth: SCENE_REFERENCE_SIZE.width,
  naturalHeight: SCENE_REFERENCE_SIZE.height,
  renderedWidth: SCENE_REFERENCE_SIZE.width,
  renderedHeight: SCENE_REFERENCE_SIZE.height,
  cropOffsetX: 0,
  cropOffsetY: 0,
  scaleX: 1 / SCENE_REFERENCE_SIZE.width,
  scaleY: 1 / SCENE_REFERENCE_SIZE.height,
  objectFit: 'cover',
  objectPosition: '50% 50%',
}

function parseObjectPosition(value: string) {
  const [x = '50%', y = '50%'] = value.split(/\s+/)
  const percent = (part: string) => part.endsWith('%') ? Number.parseFloat(part) / 100 : .5
  return { x: percent(x), y: percent(y) }
}

function calculateImageMapping(image: HTMLImageElement): ImageMapping | null {
  if (!image.naturalWidth || !image.naturalHeight || !image.clientWidth || !image.clientHeight) return null
  const style = getComputedStyle(image)
  const objectFit = style.objectFit || 'fill'
  const position = parseObjectPosition(style.objectPosition)
  const fitScale = objectFit === 'cover'
    ? Math.max(image.clientWidth / image.naturalWidth, image.clientHeight / image.naturalHeight)
    : objectFit === 'contain'
      ? Math.min(image.clientWidth / image.naturalWidth, image.clientHeight / image.naturalHeight)
      : 1
  const contentWidth = objectFit === 'fill' ? image.clientWidth : image.naturalWidth * fitScale
  const contentHeight = objectFit === 'fill' ? image.clientHeight : image.naturalHeight * fitScale
  const cropOffsetX = (image.clientWidth - contentWidth) * position.x
  const cropOffsetY = (image.clientHeight - contentHeight) * position.y
  const sourceToAssetX = image.naturalWidth / SCENE_REFERENCE_SIZE.width
  const sourceToAssetY = image.naturalHeight / SCENE_REFERENCE_SIZE.height

  return {
    naturalWidth: image.naturalWidth,
    naturalHeight: image.naturalHeight,
    renderedWidth: image.clientWidth,
    renderedHeight: image.clientHeight,
    cropOffsetX,
    cropOffsetY,
    scaleX: objectFit === 'fill' ? 1 / SCENE_REFERENCE_SIZE.width : sourceToAssetX * fitScale / image.clientWidth,
    scaleY: objectFit === 'fill' ? 1 / SCENE_REFERENCE_SIZE.height : sourceToAssetY * fitScale / image.clientHeight,
    objectFit,
    objectPosition: style.objectPosition,
  }
}

function mapSourcePoint([x, y]: readonly [number, number], mapping: ImageMapping): NormalizedPoint {
  return [mapping.cropOffsetX / mapping.renderedWidth + x * mapping.scaleX, mapping.cropOffsetY / mapping.renderedHeight + y * mapping.scaleY]
}

function mapSlot(slot: HomeSlot, mapping: ImageMapping): MappedSlot {
  return {
    target: {
      topLeft: mapSourcePoint(slot.cornersPx.topLeft, mapping),
      topRight: mapSourcePoint(slot.cornersPx.topRight, mapping),
      bottomRight: mapSourcePoint(slot.cornersPx.bottomRight, mapping),
      bottomLeft: mapSourcePoint(slot.cornersPx.bottomLeft, mapping),
    },
    mountCenter: { x: mapSourcePoint(slot.mountCenterPx, mapping)[0], y: mapSourcePoint(slot.mountCenterPx, mapping)[1] },
    lightCenter: { x: mapSourcePoint(slot.lightCenterPx, mapping)[0], y: mapSourcePoint(slot.lightCenterPx, mapping)[1] },
    horizontalVanishingPoint: slot.horizontalVanishingPointPx
      ? { x: mapSourcePoint(slot.horizontalVanishingPointPx, mapping)[0], y: mapSourcePoint(slot.horizontalVanishingPointPx, mapping)[1] }
      : undefined,
  }
}

function sourcePlaneSize(slot: HomeSlot) {
  const x = CORNERS.map((corner) => slot.cornersPx[corner][0])
  const y = CORNERS.map((corner) => slot.cornersPx[corner][1])
  return { width: Math.max(...x) - Math.min(...x), height: Math.max(...y) - Math.min(...y) }
}

function normalizedQuad(points: Record<CornerName, NormalizedPoint>, width: number, height: number): NormalizedQuad {
  return Object.fromEntries(CORNERS.map((corner) => [corner, [points[corner][0] / width, points[corner][1] / height]])) as unknown as NormalizedQuad
}

function pointLineDistance(point: NormalizedPoint, start: NormalizedPoint, end: NormalizedPoint) {
  const length = Math.hypot(end[0] - start[0], end[1] - start[1])
  if (!length) return Number.POSITIVE_INFINITY
  return Math.abs((end[1] - start[1]) * point[0] - (end[0] - start[0]) * point[1] + end[0] * start[1] - end[1] * start[0]) / length
}

function sameFrameProjection(current: FrameProjection | null, next: FrameProjection) {
  if (!current || Math.abs(current.aspectRatio - next.aspectRatio) > .000001) return false
  return CORNERS.every((corner) => Math.abs(current.quad[corner][0] - next.quad[corner][0]) < .000001
    && Math.abs(current.quad[corner][1] - next.quad[corner][1]) < .000001)
}

function mixPoint(start: NormalizedPoint, end: NormalizedPoint, amount: number): NormalizedPoint {
  return [start[0] + (end[0] - start[0]) * amount, start[1] + (end[1] - start[1]) * amount]
}

function pointInsideQuad(quad: NormalizedQuad, x: number, y: number) {
  return mixPoint(mixPoint(quad.topLeft, quad.bottomLeft, y), mixPoint(quad.topRight, quad.bottomRight, y), x)
}

function HomeFrameDepth({ aspectRatio, frameHeightCm, frameType, horizontalVanishing, orientation, quad, side, slotId }: {
  aspectRatio: number
  frameHeightCm: number
  frameType: Artwork['frameType']
  horizontalVanishing?: NormalizedCenter
  orientation: FrameOrientation
  quad: NormalizedQuad
  side: 'left' | 'right'
  slotId: HomeSlot['id']
}) {
  const depthCm = frameDepthCm(frameType)
  if (!depthCm || !horizontalVanishing || !frameHeightCm) return null

  const bounds = frameVisibleBounds(frameType, orientation)
  const frontTopLeft = pointInsideQuad(quad, bounds.left, bounds.top)
  const frontTopRight = pointInsideQuad(quad, bounds.right, bounds.top)
  const frontBottomRight = pointInsideQuad(quad, bounds.right, bounds.bottom)
  const frontBottomLeft = pointInsideQuad(quad, bounds.left, bounds.bottom)
  const frontTop = side === 'right' ? frontTopRight : frontTopLeft
  const frontBottom = side === 'right' ? frontBottomRight : frontBottomLeft
  const edgeHeightInSlotHeight = Math.hypot(
    (frontBottom[0] - frontTop[0]) * aspectRatio,
    frontBottom[1] - frontTop[1],
  )
  const projectedDepth = edgeHeightInSlotHeight * depthCm / frameHeightCm
  const awayFromVanishingPoint = (point: NormalizedPoint, scale = 1): NormalizedPoint => {
    const dx = point[0] - horizontalVanishing.x
    const dy = point[1] - horizontalVanishing.y
    const distanceInSlotHeight = Math.hypot(dx * aspectRatio, dy)
    const amount = distanceInSlotHeight ? projectedDepth * scale / distanceInSlotHeight : 0
    return [point[0] + dx * amount, point[1] + dy * amount]
  }
  const wallTop = awayFromVanishingPoint(frontTop)
  const wallBottom = awayFromVanishingPoint(frontBottom)
  const sidePoints = [frontTop, wallTop, wallBottom, frontBottom]
    .map(([x, y]) => `${x * 100},${y * 100}`)
    .join(' ')
  const rearTopLeft = awayFromVanishingPoint(frontTopLeft)
  const rearTopRight = awayFromVanishingPoint(frontTopRight)
  const rearBottomRight = awayFromVanishingPoint(frontBottomRight)
  const rearBottomLeft = awayFromVanishingPoint(frontBottomLeft)
  const frameCenterY = (frontTopLeft[1] + frontBottomRight[1]) / 2
  const capPoints = (frameCenterY >= horizontalVanishing.y
    ? [frontTopLeft, frontTopRight, rearTopRight, rearTopLeft]
    : [frontBottomLeft, frontBottomRight, rearBottomRight, rearBottomLeft])
    .map(([x, y]) => `${x * 100},${y * 100}`)
    .join(' ')
  const shadowDrop = projectedDepth * .22
  const shadowPoints = [frontTopLeft, frontTopRight, frontBottomRight, frontBottomLeft]
    .map((point) => awayFromVanishingPoint(point, 1.35))
    .map(([x, y]) => `${x * 100},${(y + shadowDrop) * 100}`)
    .join(' ')
  const colors = HOME_FRAME_DEPTH_COLORS[frameType]
  const gradientId = `home-frame-depth-${slotId}`
  const shadowId = `home-frame-shadow-${slotId}`
  const frontX = side === 'right' ? '0%' : '100%'
  const wallX = side === 'right' ? '100%' : '0%'

  return <svg
    aria-hidden="true"
    className="home-frame-depth"
    data-frame-type={frameType}
    preserveAspectRatio="none"
    viewBox="0 0 100 100"
  >
    <defs>
      <linearGradient id={gradientId} x1={frontX} x2={wallX} y1="0%" y2="0%">
        <stop offset="0%" stopColor={colors.front} stopOpacity={colors.opacity} />
        <stop offset="100%" stopColor={colors.wall} stopOpacity={colors.opacity} />
      </linearGradient>
      <filter id={shadowId} x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="0.18" />
      </filter>
    </defs>
    <polygon className="home-frame-depth__shadow" filter={`url(#${shadowId})`} points={shadowPoints} />
    <polygon className="home-frame-depth__cap" fill={colors.front} fillOpacity={colors.opacity * .72} points={capPoints} />
    <polygon className="home-frame-depth__side" fill={`url(#${gradientId})`} points={sidePoints} />
  </svg>
}

function DebugPlaneOverlay({ horizontalVanishing, projection, lightX, lightY, slot, target }: {
  horizontalVanishing?: NormalizedCenter
  projection: DebugProjection | null
  lightX: number
  lightY: number
  slot: HomeSlot
  target: NormalizedQuad
}) {
  const polygon = (quad: NormalizedQuad) => CORNERS.map((corner) => `${quad[corner][0] * 100},${quad[corner][1] * 100}`).join(' ')
  return <svg className={`home-plane-debug home-plane-debug-${slot.id}`} viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
    <polygon className="home-plane-debug-target" points={polygon(target)} />
    {projection && <>
      <polygon className="home-plane-debug-expected" points={polygon(projection.expected)} />
      <polygon className="home-plane-debug-actual" points={polygon(projection.actual)} />
      {horizontalVanishing && <g className="home-plane-debug-vanishing">
        <line x1={horizontalVanishing.x * 100} y1={horizontalVanishing.y * 100} x2={projection.actual.topRight[0] * 100} y2={projection.actual.topRight[1] * 100} vectorEffect="non-scaling-stroke" />
        <line x1={horizontalVanishing.x * 100} y1={horizontalVanishing.y * 100} x2={projection.actual.bottomRight[0] * 100} y2={projection.actual.bottomRight[1] * 100} vectorEffect="non-scaling-stroke" />
        <circle cx={horizontalVanishing.x * 100} cy={horizontalVanishing.y * 100} r="1.8" vectorEffect="non-scaling-stroke" />
        <text x={horizontalVanishing.x * 100 + 2} y={horizontalVanishing.y * 100 - 2}>HVP</text>
      </g>}
      {CORNERS.map((corner) => <text className="home-plane-debug-error" key={corner} x={projection.actual[corner][0] * 100 + 1.3} y={projection.actual[corner][1] * 100 - 1.3}>{projection.errors[corner].toFixed(2)}px</text>)}
    </>}
    {CORNERS.map((corner) => <g key={corner}>
      <circle className="home-plane-debug-point" cx={target[corner][0] * 100} cy={target[corner][1] * 100} r="1.2" vectorEffect="non-scaling-stroke" />
      <text className="home-plane-debug-corner-label" x={target[corner][0] * 100 + 1.5} y={target[corner][1] * 100 - 1.5}>{corner.replace('top', 'T').replace('bottom', 'B').replace('Left', 'L').replace('Right', 'R')}</text>
    </g>)}
    <g className="home-plane-debug-light">
      <line x1={lightX - 3} y1={lightY} x2={lightX + 3} y2={lightY} vectorEffect="non-scaling-stroke" />
      <line x1={lightX} y1={lightY - 3} x2={lightX} y2={lightY + 3} vectorEffect="non-scaling-stroke" />
      <circle cx={lightX} cy={lightY} r="1.8" vectorEffect="non-scaling-stroke" />
    </g>
  </svg>
}

function HomeArtworkMount({ artwork, debug, geometry, index, onDiagnostics, slot }: {
  artwork: Artwork
  debug: boolean
  geometry: MappedSlot
  index: number
  onDiagnostics: (slotId: HomeSlot['id'], value: DebugProjection) => void
  slot: HomeSlot
}) {
  const slotRef = useRef<HTMLAnchorElement>(null)
  const planeRef = useRef<HTMLSpanElement>(null)
  const [debugProjection, setDebugProjection] = useState<DebugProjection | null>(null)
  const [frameProjection, setFrameProjection] = useState<FrameProjection | null>(null)
  const placement = useMemo(() => normalizedQuadPlacement(geometry.target, geometry.mountCenter), [geometry])
  const canonicalSize = useMemo(() => sourcePlaneSize(slot), [slot])
  const framedSize = useMemo(() => framedMountDimensions(
    artwork.widthCm,
    artwork.heightCm,
    artwork.frameType,
  ), [artwork.frameType, artwork.heightCm, artwork.widthCm])
  const artworkRect = useMemo(() => fitPhysicalArtworkInWallRegion(
    framedSize.width,
    framedSize.height,
    canonicalSize.width,
    canonicalSize.height,
    slot.wallRegionCm.width,
    slot.wallRegionCm.height,
    EXHIBITION_WALL_SIZE_CM.width,
    EXHIBITION_WALL_SIZE_CM.height,
    slot.artworkCenter,
  ), [canonicalSize, framedSize.height, framedSize.width, slot.artworkCenter, slot.wallRegionCm])
  const horizontalVanishing = useMemo(() => geometry.horizontalVanishingPoint && ({
    x: (geometry.horizontalVanishingPoint.x - placement.left) / placement.width,
    y: (geometry.horizontalVanishingPoint.y - placement.top) / placement.height,
  }), [geometry.horizontalVanishingPoint, placement])

  useLayoutEffect(() => {
    const plane = planeRef.current
    const slotElement = slotRef.current
    if (!plane || !slotElement) return
    let animationFrame = 0

    const updateProjection = () => {
      // Use layout dimensions: transformed getBoundingClientRect() values would feed the projected AABB back into H.
      const width = plane.offsetWidth
      const height = plane.offsetHeight
      const homography = rectangleToQuadHomography(width, height, placement.localCorners)
      const matrix = homographyToMatrix3d(homography)
      plane.style.setProperty('--slot-projection', matrix)
      if (!homography) return

      cancelAnimationFrame(animationFrame)
      animationFrame = requestAnimationFrame(() => {
        const slotRect = slotElement.getBoundingClientRect()
        if (!slotRect.width || !slotRect.height) return
        const mountedArtwork = plane.querySelector<HTMLElement>('.mounted-artwork-home')
        const canonicalScaleX = width / canonicalSize.width
        const canonicalScaleY = height / canonicalSize.height
        const canonicalCorners: Record<CornerName, NormalizedPoint> = {
          topLeft: [artworkRect.left * canonicalScaleX, artworkRect.top * canonicalScaleY],
          topRight: [(artworkRect.left + artworkRect.width) * canonicalScaleX, artworkRect.top * canonicalScaleY],
          bottomRight: [(artworkRect.left + artworkRect.width) * canonicalScaleX, (artworkRect.top + artworkRect.height) * canonicalScaleY],
          bottomLeft: [artworkRect.left * canonicalScaleX, (artworkRect.top + artworkRect.height) * canonicalScaleY],
        }
        const expectedPx = Object.fromEntries(CORNERS.map((corner) => [corner, projectHomographyPoint(homography, canonicalCorners[corner])])) as Record<CornerName, NormalizedPoint>
        const expected = normalizedQuad(expectedPx, width, height)
        const nextFrameProjection = { aspectRatio: width / height, quad: expected }
        setFrameProjection((current) => sameFrameProjection(current, nextFrameProjection) ? current : nextFrameProjection)
        if (!debug) return

        const browserQuad = (mountedArtwork as QuadElement | null)?.getBoxQuads?.()[0]
        const quadPoints = browserQuad && {
          topLeft: browserQuad.p1,
          topRight: browserQuad.p2,
          bottomRight: browserQuad.p3,
          bottomLeft: browserQuad.p4,
        }
        const actual = Object.fromEntries(CORNERS.map((corner) => {
          const marker = plane.querySelector<HTMLElement>(`[data-projection-corner="${corner}"]`)
          const markerRect = marker?.getBoundingClientRect()
          const point = quadPoints?.[corner]
          return [corner, point
            ? [(point.x - slotRect.left) / slotRect.width, (point.y - slotRect.top) / slotRect.height]
            : markerRect
              ? [(markerRect.left - slotRect.left) / slotRect.width, (markerRect.top - slotRect.top) / slotRect.height]
              : expected[corner]]
        })) as unknown as NormalizedQuad
        const errors = Object.fromEntries(CORNERS.map((corner) => [corner, Math.hypot(
          (actual[corner][0] - expected[corner][0]) * slotRect.width,
          (actual[corner][1] - expected[corner][1]) * slotRect.height,
        )])) as CornerErrors
        const value = {
          actual,
          canonicalActual: {
            left: mountedArtwork?.offsetLeft ?? 0,
            top: mountedArtwork?.offsetTop ?? 0,
            width: mountedArtwork?.offsetWidth ?? 0,
            height: mountedArtwork?.offsetHeight ?? 0,
          },
          canonicalExpected: {
            left: artworkRect.left * canonicalScaleX,
            top: artworkRect.top * canonicalScaleY,
            width: artworkRect.width * canonicalScaleX,
            height: artworkRect.height * canonicalScaleY,
          },
          expected,
          errors,
          homography: matrix,
          horizontalVanishingErrorPx: horizontalVanishing ? Math.max(
            pointLineDistance(
              [horizontalVanishing.x * slotRect.width, horizontalVanishing.y * slotRect.height],
              [actual.topLeft[0] * slotRect.width, actual.topLeft[1] * slotRect.height],
              [actual.topRight[0] * slotRect.width, actual.topRight[1] * slotRect.height],
            ),
            pointLineDistance(
              [horizontalVanishing.x * slotRect.width, horizontalVanishing.y * slotRect.height],
              [actual.bottomLeft[0] * slotRect.width, actual.bottomLeft[1] * slotRect.height],
              [actual.bottomRight[0] * slotRect.width, actual.bottomRight[1] * slotRect.height],
            ),
          ) : null,
          maxErrorPx: Math.max(...Object.values(errors)),
        }
        plane.dataset.projectionErrorPx = value.maxErrorPx.toFixed(6)
        plane.dataset.homography = matrix
        setDebugProjection(value)
        onDiagnostics(slot.id, value)
      })
    }

    updateProjection()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updateProjection)
    observer?.observe(plane)
    window.addEventListener('resize', updateProjection)
    return () => {
      cancelAnimationFrame(animationFrame)
      observer?.disconnect()
      window.removeEventListener('resize', updateProjection)
    }
  }, [artworkRect, canonicalSize, debug, horizontalVanishing, onDiagnostics, placement, slot.id])

  const lightX = ((geometry.lightCenter.x - placement.left) / placement.width) * 100
  const lightY = ((geometry.lightCenter.y - placement.top) / placement.height) * 100
  const mountedLightX = ((lightX / 100 * canonicalSize.width - artworkRect.left) / artworkRect.width) * 100
  const mountedLightY = ((lightY / 100 * canonicalSize.height - artworkRect.top) / artworkRect.height) * 100
  const style = {
    '--slot-center-x': `${geometry.mountCenter.x * 100}%`,
    '--slot-center-y': `${geometry.mountCenter.y * 100}%`,
    '--slot-width': `${placement.width * 100}%`,
    '--slot-height': `${placement.height * 100}%`,
    '--slot-shadow': HOME_SHADOW_PRESETS[slot.shadowPreset],
    '--slot-light': HOME_LIGHT_PRESETS[slot.lightPreset],
    '--slot-light-x': `${mountedLightX}%`,
    '--slot-light-y': `${mountedLightY}%`,
  } as React.CSSProperties

  return <Link
    className={`home-artwork-slot home-artwork-slot-${slot.id}`}
    ref={slotRef}
    style={style}
    to={`/artworks/${artwork.id}?scene=1`}
    aria-label={`${artwork.title} 작품 전시장 보기`}
    title={artwork.title}
  >
    <span className="home-artwork-plane" ref={planeRef}>
      <ArtworkFrame
        artwork={artwork}
        mountWidthPx={canonicalSize.width}
        mountHeightPx={canonicalSize.height}
        variant="home"
        priority={index < 2}
        canonicalRect={artworkRect}
      />
      {debug && CORNERS.map((corner) => {
        const left = corner === 'topRight' || corner === 'bottomRight' ? artworkRect.left + artworkRect.width : artworkRect.left
        const top = corner === 'bottomRight' || corner === 'bottomLeft' ? artworkRect.top + artworkRect.height : artworkRect.top
        return <i
          className="projection-corner"
          data-projection-corner={corner}
          key={corner}
          style={{ left: `${left / canonicalSize.width * 100}%`, top: `${top / canonicalSize.height * 100}%` }}
          aria-hidden="true"
        />
      })}
    </span>
    {frameProjection && (slot.id === 'leftWall' || slot.id === 'rightWall') && <HomeFrameDepth
      aspectRatio={frameProjection.aspectRatio}
      frameHeightCm={framedSize.height}
      frameType={artwork.frameType}
      horizontalVanishing={horizontalVanishing}
      orientation={artwork.widthCm >= artwork.heightCm ? 'landscape' : 'portrait'}
      quad={frameProjection.quad}
      side={slot.id === 'leftWall' ? 'left' : 'right'}
      slotId={slot.id}
    />}
    {debug && <DebugPlaneOverlay horizontalVanishing={horizontalVanishing} projection={debugProjection} lightX={lightX} lightY={lightY} slot={slot} target={placement.localCorners} />}
  </Link>
}

export function HomeExhibition({ artworks }: { artworks: Artwork[] }) {
  const [searchParams] = useSearchParams()
  const debugMode = import.meta.env.DEV ? searchParams.get('debugPlanes') : null
  const debug = debugMode === '1' || debugMode === 'slot4' || debugMode === 'side'
  const debugSlot4Only = debugMode === 'slot4'
  const debugSideOnly = debugMode === 'side'
  const imageRef = useRef<HTMLImageElement>(null)
  const [imageMapping, setImageMapping] = useState(DEFAULT_IMAGE_MAPPING)
  const [diagnostics, setDiagnostics] = useState<Partial<Record<HomeSlot['id'], DebugProjection>>>({})

  const updateImageMapping = useCallback(() => {
    const next = imageRef.current && calculateImageMapping(imageRef.current)
    if (!next) return
    setImageMapping((current) => JSON.stringify(current) === JSON.stringify(next) ? current : next)
  }, [])

  useLayoutEffect(() => {
    updateImageMapping()
    const image = imageRef.current
    if (!image) return
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updateImageMapping)
    observer?.observe(image)
    window.addEventListener('resize', updateImageMapping)
    return () => {
      observer?.disconnect()
      window.removeEventListener('resize', updateImageMapping)
    }
  }, [updateImageMapping])

  const mappedSlots = useMemo(() => HOME_SLOTS.map((slot) => mapSlot(slot, imageMapping)), [imageMapping])
  const recordDiagnostics = useCallback((slotId: HomeSlot['id'], value: DebugProjection) => {
    setDiagnostics((current) => {
      const previous = current[slotId]
      if (previous?.homography === value.homography && previous.maxErrorPx.toFixed(4) === value.maxErrorPx.toFixed(4)) return current
      return { ...current, [slotId]: value }
    })
  }, [])

  useEffect(() => {
    if (!debug) return
    ;(window as Window & { __HOME_PROJECTION_DIAGNOSTICS__?: unknown }).__HOME_PROJECTION_DIAGNOSTICS__ = { imageMapping, slots: diagnostics }
  }, [debug, diagnostics, imageMapping])

  return <div className="home-scene-shell">
    <div className="home-scene-scroll" aria-label="최근 공개 작품이 걸린 전시장 전경">
      <div className="home-scene-canvas">
        <picture className="scene-background" aria-hidden="true">
          <source media="(max-width: 700px)" srcSet={HOME_SCENE.mobileAsset} />
          <img ref={imageRef} src={HOME_SCENE.asset} alt="" width="1672" height="941" fetchPriority="high" onLoad={updateImageMapping} />
        </picture>
        {artworks.slice(0, 4).map((artwork, index) => {
          const slot = HOME_SLOTS[index]
          const debugSlot = !debugSlot4Only || slot.id === 'rightWall'
          const debugSide = !debugSideOnly || slot.id === 'leftWall' || slot.id === 'rightWall'
          return <HomeArtworkMount artwork={artwork} debug={debug && debugSlot && debugSide} geometry={mappedSlots[index]} index={index} key={artwork.id} onDiagnostics={recordDiagnostics} slot={slot} />
        })}
        {debug && <aside className="home-projection-report" aria-label="Home 투영 오차 보고서">
          <strong>Geometry acceptance</strong>
          <span>asset {imageMapping.naturalWidth}×{imageMapping.naturalHeight} · rendered {imageMapping.renderedWidth.toFixed(0)}×{imageMapping.renderedHeight.toFixed(0)} · crop {imageMapping.cropOffsetX.toFixed(2)}, {imageMapping.cropOffsetY.toFixed(2)}</span>
          {HOME_SLOTS.filter((slot) => (!debugSlot4Only || slot.id === 'rightWall') && (!debugSideOnly || slot.id === 'leftWall' || slot.id === 'rightWall')).map((slot) => {
            const result = diagnostics[slot.id]
            return <span key={slot.id}><b>{slot.id}</b> {result ? <>{CORNERS.map((corner) => `${corner.replace('top', 'T').replace('bottom', 'B').replace('Left', 'L').replace('Right', 'R')} ${result.errors[corner].toFixed(2)}px`).join(' · ')}{result.horizontalVanishingErrorPx !== null && <> · HVP {result.horizontalVanishingErrorPx.toFixed(2)}px</>} · fit {Object.values(result.canonicalExpected).map((value) => value.toFixed(1)).join('/')} → DOM {Object.values(result.canonicalActual).map((value) => value.toFixed(1)).join('/')}</> : 'measuring…'}</span>
          })}
        </aside>}
      </div>
    </div>
    <span className="home-scene-pan-hint" aria-hidden="true">← 전시장을 좌우로 살펴보세요 →</span>
  </div>
}
