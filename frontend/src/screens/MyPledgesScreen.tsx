import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchMyPledges, keys, withdrawPledge } from '../api/queries'
import Link from '../routing/Link'
import { requestPath } from '../routing/routes'
import Pager from '../components/Pager'
import StatusPill from '../components/StatusPill'
import ContactPanel from '../components/ContactPanel'
import { Empty, ErrorNote, Loading } from '../components/States'

/** Every request this donor has offered on. */
export default function MyPledgesScreen() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const pledges = useQuery({
    queryKey: keys.myPledges(page),
    queryFn: () => fetchMyPledges(page),
  })

  const withdraw = useMutation({
    mutationFn: withdrawPledge,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.myPledges(page) }),
  })

  return (
    <section className="card">
      <h2>My pledges</h2>

      {pledges.isPending ? <Loading what="your pledges" /> : null}
      {pledges.error ? <ErrorNote error={pledges.error} /> : null}
      {pledges.data && pledges.data.content.length === 0 ? (
        <Empty>
          You have not offered blood yet. <Link to="/">Browse the open requests</Link>.
        </Empty>
      ) : null}

      <ul className="list">
        {(pledges.data?.content ?? []).map((entry) => (
          <li key={entry.id} className="list__row">
            <div className="list__main">
              <Link to={requestPath(entry.requestId)} className="list__title">
                {entry.patientBloodGroup} at {entry.hospital.name}
              </Link>
              <p className="list__meta">
                Offered {new Date(entry.pledgedAt).toLocaleString()}
                {entry.decidedAt ? ` · answered ${new Date(entry.decidedAt).toLocaleString()}` : ''}
              </p>

              {entry.status === 'ACCEPTED' ? (
                <ContactPanel pledgeId={entry.id} otherSide="requester" />
              ) : null}

              {entry.status === 'PENDING' || entry.status === 'ACCEPTED' ? (
                <button
                  type="button"
                  className="button button--quiet"
                  onClick={() => withdraw.mutate(entry.id)}
                  disabled={withdraw.isPending}
                >
                  Withdraw
                </button>
              ) : null}
            </div>
            <StatusPill status={entry.status} />
          </li>
        ))}
      </ul>

      {withdraw.error ? <ErrorNote error={withdraw.error} /> : null}
      {pledges.data ? <Pager page={page} data={pledges.data} onPage={setPage} /> : null}
    </section>
  )
}
