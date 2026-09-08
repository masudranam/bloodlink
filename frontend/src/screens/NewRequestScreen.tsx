import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { createRequest, fetchHospitals, keys } from '../api/queries'
import { ApiError } from '../api/client'
import { BLOOD_GROUPS } from '../api/types'
import type { BloodGroup } from '../api/types'
import { useNavigate } from '../routing/routerContext'
import { requestPath } from '../routing/routes'
import { ErrorNote, FieldError, Loading } from '../components/States'

/**
 * Raising a request.
 *
 * The hospital is a choice from the seeded list rather than a number to type.
 * `neededBy` defaults to tomorrow because the server refuses a past date and a
 * form that starts invalid is a form that teaches nothing.
 */
export default function NewRequestScreen() {
  const navigate = useNavigate()
  const hospitals = useQuery({ queryKey: keys.hospitals, queryFn: fetchHospitals })

  const [patientBloodGroup, setPatientBloodGroup] = useState<BloodGroup>('B+')
  const [hospitalId, setHospitalId] = useState('')
  const [unitsNeeded, setUnitsNeeded] = useState('1')
  const [neededBy, setNeededBy] = useState(() => {
    const tomorrow = new Date()
    tomorrow.setDate(tomorrow.getDate() + 1)
    return tomorrow.toISOString().slice(0, 10)
  })
  const [note, setNote] = useState('')

  const raise = useMutation({
    mutationFn: () =>
      createRequest({
        patientBloodGroup,
        hospitalId: Number(hospitalId),
        unitsNeeded: Number(unitsNeeded),
        neededBy,
        note: note.trim() === '' ? undefined : note.trim(),
      }),
    onSuccess: (created) => navigate(requestPath(created.id)),
  })

  const errors = raise.error instanceof ApiError ? raise.error.errors : {}

  return (
    <section className="card">
      <h2>Raise a request</h2>

      {hospitals.isPending ? <Loading what="hospitals" /> : null}
      {hospitals.error ? <ErrorNote error={hospitals.error} /> : null}

      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          raise.mutate()
        }}
      >
        <label className="field">
          <span className="field__label">The patient&apos;s blood group</span>
          <select
            value={patientBloodGroup}
            onChange={(event) => setPatientBloodGroup(event.target.value as BloodGroup)}
          >
            {BLOOD_GROUPS.map((group) => (
              <option key={group} value={group}>
                {group}
              </option>
            ))}
          </select>
          <FieldError errors={errors} field="patientBloodGroup" />
        </label>

        <label className="field">
          <span className="field__label">Hospital</span>
          <select
            value={hospitalId}
            onChange={(event) => setHospitalId(event.target.value)}
            required
          >
            <option value="">Choose a hospital</option>
            {(hospitals.data ?? []).map((hospital) => (
              <option key={hospital.id} value={hospital.id}>
                {hospital.name} ({hospital.thana})
              </option>
            ))}
          </select>
          <FieldError errors={errors} field="hospitalId" />
        </label>

        <label className="field">
          <span className="field__label">Units needed</span>
          <input
            type="number"
            min={1}
            max={10}
            value={unitsNeeded}
            onChange={(event) => setUnitsNeeded(event.target.value)}
            required
          />
          <FieldError errors={errors} field="unitsNeeded" />
        </label>

        <label className="field">
          <span className="field__label">Needed by</span>
          <input
            type="date"
            value={neededBy}
            onChange={(event) => setNeededBy(event.target.value)}
            required
          />
          <FieldError errors={errors} field="neededBy" />
        </label>

        <label className="field">
          <span className="field__label">Note (optional)</span>
          <textarea
            value={note}
            maxLength={500}
            rows={3}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Ward, contact time, anything a donor should know"
          />
          <FieldError errors={errors} field="note" />
        </label>

        <button type="submit" className="button" disabled={raise.isPending}>
          {raise.isPending ? 'Raising…' : 'Raise the request'}
        </button>
      </form>

      {raise.error && Object.keys(errors).length === 0 ? <ErrorNote error={raise.error} /> : null}

      <p className="muted">
        Your phone number is not published by raising this. A donor sees your name; the number is
        exchanged only after you accept somebody&apos;s pledge.
      </p>
    </section>
  )
}
