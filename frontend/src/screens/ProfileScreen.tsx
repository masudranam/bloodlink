import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createProfile, fetchMyProfile, fetchThanas, keys, replaceProfile } from '../api/queries'
import { ApiError } from '../api/client'
import { BLOOD_GROUPS } from '../api/types'
import type { BloodGroup, DonorProfile, ThanaSummary } from '../api/types'
import { ErrorNote, FieldError, Loading } from '../components/States'

/**
 * A donor's profile, and their eligibility as the server computed it.
 *
 * Every eligibility fact here — whether they can give, the date they next can,
 * and the interval it was derived from — comes out of the response. There is no
 * arithmetic on a date anywhere in this file, deliberately: a
 * `lastDonationDate + 90` would be pillar 2 broken on the client while the
 * server stayed right, and it would be wrong in a different way in every
 * timezone.
 */
export default function ProfileScreen() {
  const thanas = useQuery({ queryKey: keys.thanas, queryFn: fetchThanas })
  const profile = useQuery({ queryKey: keys.myProfile, queryFn: fetchMyProfile, retry: false })

  const missing = profile.error instanceof ApiError && profile.error.status === 404

  return (
    <>
      {profile.data ? (
        <section className="card">
          <h2>Eligibility</h2>
          <p className={profile.data.isEligible ? 'verdict verdict--yes' : 'verdict verdict--no'}>
            {profile.data.isEligible
              ? 'You can give blood today.'
              : `You cannot give blood yet. Next: ${profile.data.nextEligibleDate}.`}
          </p>
          <p className="muted">
            Computed from your last donation and the {profile.data.donationIntervalDays}-day
            interval every time this page is opened. It is never stored, so it cannot go stale.
          </p>
        </section>
      ) : null}

      <section className="card">
        <h2>{profile.data ? 'Your donor profile' : 'Create your donor profile'}</h2>

        {profile.isPending ? <Loading what="your profile" /> : null}
        {thanas.isPending ? <Loading what="thanas" /> : null}
        {profile.error && !missing ? <ErrorNote error={profile.error} /> : null}
        {thanas.error ? <ErrorNote error={thanas.error} /> : null}

        {missing ? (
          <p className="muted">
            You have no profile yet. It is what tells a requester your blood group and roughly
            where you are — nothing finer than your thana is ever stored.
          </p>
        ) : null}

        {profile.data || missing ? (
          // Keyed on the profile's identity so the form takes its initial values
          // from the loaded data by remounting, rather than by an effect that
          // copies the response into state on every change.
          <ProfileForm
            key={profile.data ? `profile-${profile.data.id}` : 'new-profile'}
            existing={profile.data}
            thanas={thanas.data ?? []}
          />
        ) : null}

        <p className="muted">
          Turning availability off keeps you out of search results. You can still offer blood
          yourself on any request.
        </p>
      </section>
    </>
  )
}

function ProfileForm({
  existing,
  thanas,
}: {
  existing: DonorProfile | undefined
  thanas: ThanaSummary[]
}) {
  const queryClient = useQueryClient()
  const [bloodGroup, setBloodGroup] = useState<BloodGroup>(existing?.bloodGroup ?? 'B+')
  const [thanaId, setThanaId] = useState(existing ? String(existing.thana.id) : '')
  const [lastDonationDate, setLastDonationDate] = useState(existing?.lastDonationDate ?? '')
  const [available, setAvailable] = useState(existing?.available ?? true)

  const save = useMutation({
    mutationFn: () => {
      const body = {
        bloodGroup,
        thanaId: Number(thanaId),
        lastDonationDate: lastDonationDate === '' ? null : lastDonationDate,
        available,
      }
      return existing ? replaceProfile(body) : createProfile(body)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.myProfile }),
  })

  const errors = save.error instanceof ApiError ? save.error.errors : {}

  return (
    <>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate()
        }}
      >
        <label className="field">
          <span className="field__label">Blood group</span>
          <select
            value={bloodGroup}
            onChange={(event) => setBloodGroup(event.target.value as BloodGroup)}
          >
            {BLOOD_GROUPS.map((group) => (
              <option key={group} value={group}>
                {group}
              </option>
            ))}
          </select>
          <FieldError errors={errors} field="bloodGroup" />
        </label>

        <label className="field">
          <span className="field__label">Thana</span>
          <select value={thanaId} onChange={(event) => setThanaId(event.target.value)} required>
            <option value="">Choose your thana</option>
            {thanas.map((thana) => (
              <option key={thana.id} value={thana.id}>
                {thana.name}, {thana.district}
              </option>
            ))}
          </select>
          <FieldError errors={errors} field="thanaId" />
        </label>

        <label className="field">
          <span className="field__label">Last donation (leave blank if never)</span>
          <input
            type="date"
            value={lastDonationDate}
            onChange={(event) => setLastDonationDate(event.target.value)}
          />
          <FieldError errors={errors} field="lastDonationDate" />
        </label>

        <label className="checkbox">
          <input
            type="checkbox"
            checked={available}
            onChange={(event) => setAvailable(event.target.checked)}
          />
          I am available to be asked
        </label>

        <button type="submit" className="button" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : existing ? 'Save changes' : 'Create profile'}
        </button>
      </form>

      {save.error && Object.keys(errors).length === 0 ? <ErrorNote error={save.error} /> : null}
    </>
  )
}
