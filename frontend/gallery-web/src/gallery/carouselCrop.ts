import type { CSSProperties } from 'react'

const FRAME_WIDTH = 16
const FRAME_HEIGHT = 9

const clamp = (value: number, minimum: number, maximum: number) =>
  Math.max(minimum, Math.min(maximum, value))

const finiteOr = (value: number, fallback: number) => Number.isFinite(value) ? value : fallback

export type CarouselCropLayout = {
  focalX: number
  focalY: number
  zoom: number
  minFocalX: number
  maxFocalX: number
  minFocalY: number
  maxFocalY: number
  renderedWidth: number
  renderedHeight: number
  left: number
  top: number
}

export function createCarouselCropLayout(
  sourceWidth: number,
  sourceHeight: number,
  focalX: number,
  focalY: number,
  zoom: number,
): CarouselCropLayout {
  const width = Math.max(1, finiteOr(sourceWidth, FRAME_WIDTH))
  const height = Math.max(1, finiteOr(sourceHeight, FRAME_HEIGHT))
  const safeZoom = clamp(finiteOr(zoom, 1), 1, 3)
  const coverScale = Math.max(FRAME_WIDTH / width, FRAME_HEIGHT / height)
  const renderedWidth = width * coverScale * safeZoom
  const renderedHeight = height * coverScale * safeZoom
  const minFocalX = Math.min(.5, FRAME_WIDTH / (2 * renderedWidth))
  const maxFocalX = Math.max(.5, 1 - minFocalX)
  const minFocalY = Math.min(.5, FRAME_HEIGHT / (2 * renderedHeight))
  const maxFocalY = Math.max(.5, 1 - minFocalY)
  const safeFocalX = clamp(finiteOr(focalX, .5), minFocalX, maxFocalX)
  const safeFocalY = clamp(finiteOr(focalY, .5), minFocalY, maxFocalY)

  return {
    focalX: safeFocalX,
    focalY: safeFocalY,
    zoom: safeZoom,
    minFocalX,
    maxFocalX,
    minFocalY,
    maxFocalY,
    renderedWidth,
    renderedHeight,
    left: (FRAME_WIDTH / 2) - (safeFocalX * renderedWidth),
    top: (FRAME_HEIGHT / 2) - (safeFocalY * renderedHeight),
  }
}

export function dragCarouselCrop(
  layout: CarouselCropLayout,
  horizontalFrameRatio: number,
  verticalFrameRatio: number,
) {
  const left = clamp(
    layout.left + (finiteOr(horizontalFrameRatio, 0) * FRAME_WIDTH),
    FRAME_WIDTH - layout.renderedWidth,
    0,
  )
  const top = clamp(
    layout.top + (finiteOr(verticalFrameRatio, 0) * FRAME_HEIGHT),
    FRAME_HEIGHT - layout.renderedHeight,
    0,
  )
  return {
    focalX: clamp((FRAME_WIDTH / 2 - left) / layout.renderedWidth, layout.minFocalX, layout.maxFocalX),
    focalY: clamp((FRAME_HEIGHT / 2 - top) / layout.renderedHeight, layout.minFocalY, layout.maxFocalY),
  }
}

export function carouselCropStyle(layout: CarouselCropLayout) {
  return {
    '--carousel-image-width': `${(layout.renderedWidth / FRAME_WIDTH) * 100}%`,
    '--carousel-image-height': `${(layout.renderedHeight / FRAME_HEIGHT) * 100}%`,
    '--carousel-image-left': `${(layout.left / FRAME_WIDTH) * 100}%`,
    '--carousel-image-top': `${(layout.top / FRAME_HEIGHT) * 100}%`,
  } as CSSProperties
}
