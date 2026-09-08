import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchMyReveals, keys } from '../api/queries'
import Link from '../routing/Link'
import { requestPath } from '../routing/routes'
import Pager from '../components/Pager'
import { Empty, ErrorNote, Loading } from '../components/States'

/**
 * Who has seen your phone number.
 *
 * The screen that makes the promise checkable by the person it was made to. It
 * carries no phone number at all - not the viewer's, and not the reader's own.
 */
export default function RevealsScreen() {
  const [page, setPage] = useState(0)
  const reveals = useQuery({
    queryKey: keys.myReveals(page),
    queryFn: () => fetchMyReveals(page),
  })

  return (
    <section className="card">
      <h2>Who has seen my number</h2>

      <p className="muted">
        Every time somebody is shown your phone number it is recorded here, with their name and
        the time. Nothing can remove an entry.
      </p>

      {reveals.isPending ? <Loading what="the reveal log" /> : null}
      {reveals.error ? <ErrorNote error={reveals.error} /> : null}
      {reveals.data && reveals.data.content.length === 0 ? (
        <Empty>Nobody has been shown your number.</Empty>
      ) : null}

      <ul className="list">
        {(reveals.data?.content ?? []).map((entry) => (
          <li key={entry.id} className="list__row">
            <div className="list__main">
              <p className="list__title">
                {entry.viewer.fullName} <span className="contact__role">{entry.viewer.role}</span>
              </p>
              <p className="list__meta">
                {new Date(entry.revealedAt).toLocaleString()} &middot;{' '}
                <Link to={requestPath(entry.requestId)}>request {entry.requestId}</Link>, pledge{' '}
                {entry.pledgeId}
              </p>
            </div>
          </li>
        ))}
      </ul>

      {reveals.data ? <Pager page={page} data={reveals.data} onPage={setPage} /> : null}
    </section>
  )
}
