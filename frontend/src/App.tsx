export default function App() {
  return (
    <main className="shell">
      <header className="shell__header">
        <h1>RoktoLink</h1>
        <p className="shell__tagline">A privacy-first blood donor network for Bangladesh.</p>
      </header>

      <section className="card">
        <h2>Skeleton</h2>
        <p>
          No screens yet. The UI is built in issue 9, once the specs it depends on are merged.
          See <code>docs/BACKLOG.md</code>.
        </p>
      </section>

      <section className="card">
        <h2>The three rules this project exists to enforce</h2>
        <ol className="rules">
          <li>
            <strong>Phone numbers are never in a list response.</strong> A number is revealed only
            after a pledge is accepted, and every reveal is audited.
          </li>
          <li>
            <strong>Eligibility is computed, never stored.</strong>{' '}
            <code>lastDonationDate + interval &lt; today</code>, interval from config.
          </li>
          <li>
            <strong>Compatibility is a pure function.</strong> One dependency-free service, an 8x8
            matrix, tested exhaustively.
          </li>
        </ol>
      </section>
    </main>
  )
}
