import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { login } from '../api/queries'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/authContext'
import { useNavigate, useRouter } from '../routing/routerContext'
import Link from '../routing/Link'
import { ErrorNote, FieldError } from '../components/States'

/**
 * Signing in, and continuing to wherever the person was going.
 *
 * The redirect target rides in the query string, put there by App when it
 * intercepted a protected route. That is what makes a pasted deep link survive a
 * login instead of dumping the person on the feed (SPEC-009 AC-5).
 */
export default function LoginScreen() {
  const { signIn, expiredMessage } = useAuth()
  const navigate = useNavigate()
  const { path } = useRouter()
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')

  const next = new URLSearchParams(window.location.search).get('next')

  const signInMutation = useMutation({
    mutationFn: () => login(phone, password),
    onSuccess: (response) => {
      signIn({ token: response.accessToken, role: response.role })
      navigate(next && next.startsWith('/') ? next : '/', { replace: true })
    },
  })

  const errors = signInMutation.error instanceof ApiError ? signInMutation.error.errors : {}

  return (
    <section className="card">
      <h2>Sign in</h2>

      {expiredMessage ? <ErrorNote error={new Error(expiredMessage)} /> : null}

      {next ? (
        <p className="muted">
          You will be taken back to <code>{next}</code> once you are signed in.
        </p>
      ) : null}

      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault()
          signInMutation.mutate()
        }}
      >
        <label className="field">
          <span className="field__label">Phone</span>
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            placeholder="01712345678"
            autoComplete="username"
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
            autoComplete="current-password"
            required
          />
          <FieldError errors={errors} field="password" />
        </label>

        <button type="submit" className="button" disabled={signInMutation.isPending}>
          {signInMutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      {signInMutation.error && Object.keys(errors).length === 0 ? (
        <ErrorNote error={signInMutation.error} />
      ) : null}

      <p className="muted">
        No account yet? <Link to={path === '/register' ? '/login' : '/register'}>Register</Link>.
      </p>
    </section>
  )
}
