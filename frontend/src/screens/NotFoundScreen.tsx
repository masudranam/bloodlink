import Link from '../routing/Link'

export default function NotFoundScreen() {
  return (
    <section className="card">
      <h2>Not a page</h2>
      <p>
        There is nothing at this address. <Link to="/">Back to the feed</Link>.
      </p>
    </section>
  )
}
