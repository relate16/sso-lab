import { useCallback, useEffect, useRef, useState } from 'react'
import { publicApi } from '../api'
import type { ArtworkEntrySource, ArtworkViewSurface } from '../types'

const VIEW_DELAY_MS = 1500
const VIEW_THRESHOLD = .5

export function useArtworkView(artworkId: string | null | undefined,
  surface: ArtworkViewSurface, entrySource: ArtworkEntrySource, enabled = true) {
  const [element, setElement] = useState<HTMLElement | null>(null)
  const recorded = useRef<string | null>(null)
  const ref = useCallback((node: HTMLElement | null) => setElement(node), [])

  useEffect(() => {
    if (!enabled || !artworkId || !element) return
    const key = `${artworkId}:${surface}`
    if (recorded.current === key) return
    let timer: number | null = null
    let visibleEnough = false

    const cancel = () => {
      if (timer !== null) window.clearTimeout(timer)
      timer = null
    }
    const arm = () => {
      cancel()
      if (!visibleEnough || document.visibilityState !== 'visible' || recorded.current === key) return
      timer = window.setTimeout(() => {
        if (!visibleEnough || document.visibilityState !== 'visible') return
        recorded.current = key
        void publicApi.view(artworkId, surface, entrySource).catch(() => undefined)
      }, VIEW_DELAY_MS)
    }
    const observer = new IntersectionObserver(([entry]) => {
      visibleEnough = entry.isIntersecting && entry.intersectionRatio >= VIEW_THRESHOLD
      if (visibleEnough) arm()
      else cancel()
    }, { threshold: [VIEW_THRESHOLD] })
    const visibility = () => document.visibilityState === 'visible' ? arm() : cancel()
    observer.observe(element)
    document.addEventListener('visibilitychange', visibility)
    return () => {
      cancel()
      observer.disconnect()
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [artworkId, element, enabled, entrySource, surface])

  return ref
}
