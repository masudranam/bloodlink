import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  acceptPledge,
  cancelRequest,
  declinePledge,
  fetchDonors,
  fetchMe,
  fetchMyPledges,
  fetchPledgesOnRequest,
  fetchRequest,
  fulfilRequest,
  keys,
  pledge as makePledge,
  withdrawPledge,
} from '../api/queries'
import type { PledgeSummary } from '../api/types'
import { useAuth } from '../auth/authContext'
import Pager from '../components/Pager'
import StatusPill from '../components/StatusPill'
import ContactPanel from '../components/ContactPanel'
import { Empty, ErrorNote, Loading } from '../components/States'

/**
 * One request, from whichever side you are on.
 *
 * A requester who raised it gets the ranked donor list and the pledges, with
 * accept and decline. A donor gets a pledge button, and their own pledge's
 * status once they have made one. Both get the contact panel once a pledge is
 * accepted — and in both cases the number is behind a press, never fetched by
 * opening this page.
 */
export default function RequestScreen({ id }: { id: number }) {
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: keys.me, queryFn: fetchMe })
  const request = useQuery({ queryKey: keys.request(id), queryFn: () => fetchRequest(id) })

  const isRequester = session?.role === 'REQUESTER'
  const isOwner = Boolean(me.data && request.data && request.data.requester.id === me.data.id)

  if (request.isPending || me.isPending) {
    return (
      <section className="card">
        <Loading what="the request" />
      </section>
    )
  }

  if (request.error || !request.data) {
    return (
      <section className="card">
        <h2>Request</h2>
        <ErrorNote error={request.error ?? new Error('The request could not be loaded.')} />
      </section>
    )
  }

  const detail = request.data

  return (
    <>
      <section className="card">
        <div className="row row--spread">
          <h2>
            {detail.patientBloodGroup} &middot; {detail.unitsNeeded}{' '}
            {detail.unitsNeeded === 1 ? 'unit' : 'units'}
          </h2>
          <StatusPill status={detail.status} />
        </div>
        <p className="list__meta">
          {detail.hospital.name}, {detail.hospital.thana} &middot; needed by {detail.neededBy}
        </p>
        <p className="list__meta">Raised by {detail.requester.fullName}</p>
        {detail.note ? <p className="list__note">{detail.note}</p> : null}

        {isOwner ? (
          <OwnerActions
            id={id}
            status={detail.status}
            onDone={() => queryClient.invalidateQueries({ queryKey: keys.request(id) })}
          />
        ) : null}
      </section>

      {isRequester && isOwner ? <PledgesOnRequest id={id} /> : null}
      {isRequester && isOwner && !detail.status.match(/FULFILLED|CANCELLED|EXPIRED/) ? (
        <DonorSearch id={id} />
      ) : null}
      {!isRequester ? <DonorSide id={id} /> : null}
    </>
  )
}

function OwnerActions({
  id,
  status,
  onDone,
}: {
  id: number
  status: string
  onDone: () => void
}) {
  const cancel = useMutation({ mutationFn: () => cancelRequest(id), onSuccess: onDone })
  const fulfil = useMutation({ mutationFn: () => fulfilRequest(id), onSuccess: onDone })

  if (status === 'FULFILLED' || status === 'CANCELLED' || status === 'EXPIRED') {
    return <p className="muted">This request is closed. Nothing further can be done to it.</p>
  }

  return (
    <div className="row row--actions">
      <button
        type="button"
        className="button button--quiet"
        onClick={() => cancel.mutate()}
        disabled={cancel.isPending}
      >
        Cancel the request
      </button>
      <button
        type="button"
        className="button"
        onClick={() => fulfil.mutate()}
        disabled={fulfil.isPending}
      >
        Blood was given
      </button>
      {cancel.error ? <ErrorNote error={cancel.error} /> : null}
      {fulfil.error ? <ErrorNote error={fulfil.error} /> : null}
    </div>
  )
}

function PledgesOnRequest({ id }: { id: number }) {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const pledges = useQuery({
    queryKey: keys.pledgesOnRequest(id, page),
    queryFn: () => fetchPledgesOnRequest(id, page),
  })

  // A decision changes the pledge and can change the request's status, and
  // nothing else. Invalidating exactly those two is why the query keys mirror
  // the endpoints.
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: keys.pledgesOnRequest(id, page) })
    queryClient.invalidateQueries({ queryKey: keys.request(id) })
  }

  const accept = useMutation({ mutationFn: acceptPledge, onSuccess: refresh })
  const decline = useMutation({ mutationFn: declinePledge, onSuccess: refresh })

  return (
    <section className="card">
      <h2>Pledges</h2>

      {pledges.isPending ? <Loading what="pledges" /> : null}
      {pledges.error ? <ErrorNote error={pledges.error} /> : null}
      {pledges.data && pledges.data.content.length === 0 ? (
        <Empty>Nobody has offered yet. Donors below can be asked directly.</Empty>
      ) : null}

      <ul className="list">
        {(pledges.data?.content ?? []).map((entry) => (
          <li key={entry.id} className="list__row">
            <div className="list__main">
              <p className="list__title">
                {entry.donor.fullName} &middot; {entry.donor.bloodGroup}
              </p>
              <p className="list__meta">
                {entry.donor.thana.name} &middot; offered{' '}
                {new Date(entry.pledgedAt).toLocaleString()}
              </p>

              {entry.status === 'PENDING' ? (
                <div className="row row--actions">
                  <button
                    type="button"
                    className="button"
                    onClick={() => accept.mutate(entry.id)}
                    disabled={accept.isPending}
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    className="button button--quiet"
                    onClick={() => decline.mutate(entry.id)}
                    disabled={decline.isPending}
                  >
                    Decline
                  </button>
                </div>
              ) : null}

              {entry.status === 'ACCEPTED' ? (
                <ContactPanel pledgeId={entry.id} otherSide="donor" />
              ) : null}
            </div>
            <StatusPill status={entry.status} />
          </li>
        ))}
      </ul>

      {accept.error ? <ErrorNote error={accept.error} /> : null}
      {decline.error ? <ErrorNote error={decline.error} /> : null}
      {pledges.data ? <Pager page={page} data={pledges.data} onPage={setPage} /> : null}
    </section>
  )
}

function DonorSearch({ id }: { id: number }) {
  const [radiusKm, setRadiusKm] = useState(10)
  const [page, setPage] = useState(0)
  const donors = useQuery({
    queryKey: keys.donors(id, radiusKm, page),
    queryFn: () => fetchDonors(id, radiusKm, page),
  })

  return (
    <section className="card">
      <div className="row row--spread">
        <h2>Donors who could help</h2>
        <label className="inline-field">
          <span>Within</span>
          <select
            value={radiusKm}
            onChange={(event) => {
              setRadiusKm(Number(event.target.value))
              setPage(0)
            }}
          >
            {[2, 5, 10, 25, 50].map((option) => (
              <option key={option} value={option}>
                {option} km
              </option>
            ))}
          </select>
        </label>
      </div>

      <p className="muted">
        Compatible with the patient, eligible today, available, nearest first. No phone numbers
        here — these people have not been asked yet.
      </p>

      {donors.isPending ? <Loading what="donors" /> : null}
      {donors.error ? <ErrorNote error={donors.error} /> : null}
      {donors.data && donors.data.content.length === 0 ? (
        <Empty>No eligible donor within {radiusKm} km. Try a wider radius.</Empty>
      ) : null}

      <ul className="list">
        {(donors.data?.content ?? []).map((donor) => (
          <li key={donor.donorId} className="list__row">
            <div className="list__main">
              <p className="list__title">
                {donor.fullName} &middot; {donor.bloodGroup}
              </p>
              <p className="list__meta">
                {donor.thana.name} &middot; {donor.distanceKm.toFixed(1)} km
                {donor.lastDonationDate ? ` · last gave ${donor.lastDonationDate}` : ' · never given'}
              </p>
            </div>
          </li>
        ))}
      </ul>

      {donors.data ? <Pager page={page} data={donors.data} onPage={setPage} /> : null}
    </section>
  )
}

function DonorSide({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const mine = useQuery({ queryKey: keys.myPledges(0), queryFn: () => fetchMyPledges(0) })

  const existing: PledgeSummary | undefined = mine.data?.content.find(
    (entry) => entry.requestId === id,
  )

  const pledgeNow = useMutation({
    mutationFn: () => makePledge(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.myPledges(0) })
      queryClient.invalidateQueries({ queryKey: keys.request(id) })
    },
  })

  const withdraw = useMutation({
    mutationFn: () => withdrawPledge(existing?.id ?? 0),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.myPledges(0) })
      queryClient.invalidateQueries({ queryKey: keys.request(id) })
    },
  })

  return (
    <section className="card">
      <h2>Your pledge</h2>

      {mine.isPending ? <Loading what="your pledges" /> : null}
      {mine.error ? <ErrorNote error={mine.error} /> : null}

      {!existing && mine.data ? (
        <>
          <p>
            Offering blood tells the requester you are willing. Your phone number stays private
            until they accept.
          </p>
          <button
            type="button"
            className="button"
            onClick={() => pledgeNow.mutate()}
            disabled={pledgeNow.isPending}
          >
            {pledgeNow.isPending ? 'Offering…' : 'I will give blood'}
          </button>
          {pledgeNow.error ? <ErrorNote error={pledgeNow.error} /> : null}
        </>
      ) : null}

      {existing ? (
        <>
          <div className="row row--spread">
            <p className="list__meta">
              Offered {new Date(existing.pledgedAt).toLocaleString()}
            </p>
            <StatusPill status={existing.status} />
          </div>

          {existing.status === 'ACCEPTED' ? (
            <ContactPanel pledgeId={existing.id} otherSide="requester" />
          ) : null}

          {existing.status === 'PENDING' || existing.status === 'ACCEPTED' ? (
            <button
              type="button"
              className="button button--quiet"
              onClick={() => withdraw.mutate()}
              disabled={withdraw.isPending}
            >
              Withdraw my pledge
            </button>
          ) : null}
          {withdraw.error ? <ErrorNote error={withdraw.error} /> : null}
        </>
      ) : null}
    </section>
  )
}
