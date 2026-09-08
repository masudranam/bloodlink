import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { login, registerUser } from '../api/queries'
import { ApiError } from '../api/client'
import type { UserRole } from '../api/types'
import { useAuth } from '../auth/authContext'
import { useNavigate } from '../routing/routerContext'
import Link from '../routing/Link'
import { ErrorNote, FieldError } from '../components/States'

/**
 * Registering, then signing straight in.
 *
 * Two calls rather than one: register returns who you are, not a token, and
 * asking somebody to type their password again immediately would be rude.
 */
export default function RegisterScreen() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>('DONOR')

  const register = useMutation({
    mutationFn: async () => {
      await registerUser(fullName, phone, password, role)
      return login(phone, password)
    },
    onSuccess: (response) => {
      signIn({ token: response.accessToken, role: response.role })
      navigate(response.role === 'DONOR' ? '/profile' : '/requests/new', { replace: true })
    },
  })

  const errors = register.error instanceof ApiError ? register.error.errors : {}

  return (
    <section className="card">
      <h2>Register</h2>

      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          register.mutate()
        }}
      >
        <label className="field">
          <span className="field__label">Full name</span>
          <input value={fullName} onChange={(event) => setFullName(event.target.value)} required />
          <FieldError errors={errors} field="fullName" />
        </label>

        <label className="field">
          <span className="field__label">Phone</span>
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            placeholder="01712345678"
            required
          />
          <FieldError errors={errors} field="phone" />
        </label>

        <label className="field">
          <span className="field__label">Password</span>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            required
          />
          <FieldError errors={errors} field="password" />
        </label>

        <fieldset className="field field--radios">
          <legend className="field__label">I am</legend>
          <label className="radio">
            <input
              type="radio"
              name="role"
              checked={role === 'DONOR'}
              onChange={() => setRole('DONOR')}
            />
            a donor, offering blood
          </label>
          <label className="radio">
            <input
              type="radio"
              name="role"
              checked={role === 'REQUESTER'}
              onChange={() => setRole('REQUESTER')}
            />
            a requester, looking for blood
          </label>
          <FieldError errors={errors} field="role" />
        </fieldset>

        <button type="submit" className="button" disabled={register.isPending}>
          {register.isPending ? 'Creating…' : 'Create account'}
        </button>
      </form>

      {register.error && Object.keys(errors).length === 0 ? (
        <ErrorNote error={register.error} />
      ) : null}

      <p className="muted">
        Already registered? <Link to="/login">Sign in</Link>.
      </p>
    </section>
  )
}
