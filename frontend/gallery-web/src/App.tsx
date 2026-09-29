import { Link, Route, Routes } from 'react-router-dom'
import { AboutPage, ExhibitionEntry, HomePage, WorksPage } from './gallery/PublicPages'
import { GalleryScene } from './gallery/GalleryScene'
import { StudioApp } from './studio/StudioApp'

function GalleryShell({ children }: { children: React.ReactNode }) {
  return <div className="gallery-shell">
    <header className="gallery-header">
      <Link className="wordmark" to="/">Quiet Winter Gallery</Link>
      <nav aria-label="주요 메뉴">
        <Link to="/">홈</Link>
        <Link to="/works">작품</Link>
        <Link to="/exhibition">전시실</Link>
        <Link to="/about">소개</Link>
      </nav>
    </header>
    <main>{children}</main>
    <footer><span>Quiet Winter Gallery</span><Link to="/studio">Artist Studio</Link></footer>
  </div>
}

export function App() {
  return <Routes>
    <Route path="/studio/*" element={<StudioApp />} />
    <Route path="*" element={<GalleryShell><Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/exhibition" element={<ExhibitionEntry />} />
      <Route path="/works" element={<WorksPage />} />
      <Route path="/about" element={<AboutPage />} />
      <Route path="/artworks/:id" element={<GalleryScene />} />
    </Routes></GalleryShell>} />
  </Routes>
}
