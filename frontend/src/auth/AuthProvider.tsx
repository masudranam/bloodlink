import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setToken, setUnauthorizedHandler } from '../api/client'
import { clearSession, readSession, writeSession } from './session'
import type { Session } from './session'
import { AuthContext } from './authContext'
import type { AuthState } from './authContext'

const EXPIRED = 'Your session ended. Please sign in again.'

/**
 * Holds the session and keeps the fetch wrapper in step with it.
 *
 * The 401 handler is registered here rather than in the client, because dropping
 * a session is a decision about application state and the client only knows
 * about HTTP. Every cached query is cleared on sign-out, so the next person to
 * sign in on this browser never sees the previous one's data.
 */
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const existing = readSession()
    setToken(existing ? existing.token : null)
    return existing
  })
  const [expiredMessage, setExpiredMessage] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const signIn = useCallback(
    (next: Session) => {
      writeSession(next)
      setToken(next.token)
      setExpiredMessage(null)
      setSession(next)
    },
    [],
  )

  const signOut = useCallback(() => {
    clearSession()
    setToken(null)
    setSession(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession()
      setToken(null)
      setSession(null)
      setExpiredMessage(EXPIRED)
      queryClient.clear()
    })
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({
      session,
      expiredMessage,
      signIn,
      signOut,
      clearExpiredMessage: () => setExpiredMessage(null),
    }),
    [session, expiredMessage, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
