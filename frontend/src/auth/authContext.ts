import { createContext, useContext } from 'react'
import type { Session } from './session'

export interface AuthState {
  session: Session | null
  /** Set on the login screen when a call came back 401 and the session was dropped. */
  expiredMessage: string | null
  signIn: (session: Session) => void
  signOut: () => void
  clearExpiredMessage: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) {
    throw new Error('useAuth used outside AuthProvider')
  }
  return state
}
