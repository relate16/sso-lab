import { Link, Route, Routes } from 'react-router-dom'

function GalleryShell({ children }: { children: React.ReactNode }) {
  return <div className="gallery-shell">
    <header className="gallery-header">
      <Link className="wordmark" to="/">Quiet Winter Gallery</Link>
      <nav aria-label="주요 메뉴">
        <Link to="/">Home</Link>
        <Link to="/works">Works</Link>
        <Link to="/about">About</Link>
      </nav>
    </header>
    <main>{children}</main>
    <footer><span>Quiet Winter Gallery</span><Link to="/studio">Artist Studio</Link></footer>
  </div>
}

function FoundationPage({ title, copy }: { title: string; copy: string }) {
  return <section className="foundation-page">
    <p className="eyebrow">Quiet Winter Gallery</p>
    <h1>{title}</h1>
    <p>{copy}</p>
  </section>
}

export function App() {
  return <GalleryShell>
    <Routes>
      <Route path="/" element={<FoundationPage title="A quiet place for winter light." copy="작품과 공간, 벽면의 설명이 함께 머무는 온라인 전시를 준비하고 있습니다." />} />
      <Route path="/works" element={<FoundationPage title="Works" copy="공개 작품을 빠르게 찾고 전시장 장면으로 이동하는 공간입니다." />} />
      <Route path="/about" element={<FoundationPage title="About" copy="작업 방식과 작품에 관한 이야기를 전합니다." />} />
      <Route path="/studio/*" element={<FoundationPage title="Studio" copy="관리자 전용 작품 관리 영역입니다." />} />
    </Routes>
  </GalleryShell>
}
