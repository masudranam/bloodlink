import { useQueries, useQuery } from '@tanstack/react-query'
import { fetchFeed, fetchMe, keys } from '../api/queries'
import { REQUEST_STATUSES } from '../api/types'
import type { RequestSummary } from '../api/types'
import Link from '../routing/Link'
import { requestPath } from '../routing/routes'
import StatusPill from '../components/StatusPill'
import { Empty, ErrorNote, Loading } from '../components/States'

/**
 * A requester's own requests.
 *
 * **This screen is a known compromise, recorded in SPEC-009.** The API has no
 * owner filter on the feed, so it reads the first page of every status and keeps
 * the rows whose requester is the signed-in user. That is correct for a project
 * of this size and wrong at scale — five calls to answer one question, and only
 * the first page of each.
 *
 * The fix is a `mine` filter on `GET /api/requests`, which changes SPEC-006's
 * merged contract and so needs its own spec rather than being added quietly
 * here. Until then this is honest about what it is.
 */
export default function MyRequestsScreen() {
  const me = useQuery({ queryKey: keys.me, queryFn: fetchMe })

  const perStatus = useQueries({
    queries: REQUEST_STATUSES.map((status) => ({
      queryKey: keys.feed(status, 0),
      queryFn: () => fetchFeed(status, 0),
      enabled: me.data !== undefined,
    })),
  })

  const loading = me.isPending || perStatus.some((query) => query.isPending)
  const failed = me.error ?? perStatus.find((query) => query.error)?.error

  const mine: RequestSummary[] = me.data
    ? perStatus
        .flatMap((query) => query.data?.content ?? [])
        .filter((request) => request.requester.id === me.data.id)
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
    : []

  return (
    <section className="card">
      <h2>My requests</h2>

      {loading ? <Loading what="your requests" /> : null}
      {failed ? <ErrorNote error={failed} /> : null}

      {!loading && !failed && mine.length === 0 ? (
        <Empty>
          You have not raised a request yet. <Link to="/requests/new">Raise one</Link>.
        </Empty>
      ) : null}

      <ul className="list">
        {mine.map((request) => (
          <li key={request.id} className="list__row">
            <div className="list__main">
              <Link to={requestPath(request.id)} className="list__title">
                {request.patientBloodGroup} &middot; {request.unitsNeeded}{' '}
                {request.unitsNeeded === 1 ? 'unit' : 'units'}
              </Link>
              <p className="list__meta">
                {request.hospital.name} &middot; needed by {request.neededBy}
              </p>
            </div>
            <StatusPill status={request.status} />
          </li>
        ))}
      </ul>

      {mine.length > 0 ? (
        <p className="muted">
          Showing the first page of each status. The API has no owner filter yet, so this screen
          filters the feed itself.
        </p>
      ) : null}
    </section>
  )
}
