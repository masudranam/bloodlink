import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchFeed, keys } from '../api/queries'
import { requestPath } from '../routing/routes'
import Link from '../routing/Link'
import Pager from '../components/Pager'
import StatusPill from '../components/StatusPill'
import { Empty, ErrorNote, Loading } from '../components/States'

/**
 * Open requests, newest first.
 *
 * Readable by both roles: a donor browsing it is the entire point, and a
 * requester seeing what else is being asked for costs nothing. No phone numbers,
 * because the type cannot hold one.
 */
export default function FeedScreen() {
  const [page, setPage] = useState(0)
  const feed = useQuery({ queryKey: keys.feed('OPEN', page), queryFn: () => fetchFeed('OPEN', page) })

  return (
    <section className="card">
      <h2>Open requests</h2>

      {feed.isPending ? <Loading what="the feed" /> : null}
      {feed.error ? <ErrorNote error={feed.error} /> : null}

      {feed.data && feed.data.content.length === 0 ? (
        <Empty>Nobody is asking for blood right now.</Empty>
      ) : null}

      {feed.data ? (
        <ul className="list">
          {feed.data.content.map((request) => (
            <li key={request.id} className="list__row">
              <div className="list__main">
                <Link to={requestPath(request.id)} className="list__title">
                  {request.patientBloodGroup} &middot; {request.unitsNeeded}{' '}
                  {request.unitsNeeded === 1 ? 'unit' : 'units'}
                </Link>
                <p className="list__meta">
                  {request.hospital.name}, {request.hospital.thana} &middot; needed by{' '}
                  {request.neededBy}
                </p>
                {request.note ? <p className="list__note">{request.note}</p> : null}
              </div>
              <StatusPill status={request.status} />
            </li>
          ))}
        </ul>
      ) : null}

      {feed.data ? <Pager page={page} data={feed.data} onPage={setPage} /> : null}
    </section>
  )
}
