/**
 * Where the token lives.
 *
 * One key in localStorage, read once on load. This is a known weakness and
 * SPEC-009 records it: any script on the origin can read it, so an XSS becomes a
 * stolen token. The alternative is an httpOnly cookie, which ADR-0003 ruled out
 * for a cross-origin SPA, so it is a stated cost rather than an oversight.
 *
 * The role is stored alongside it because it comes from the login response. The
 * client deliberately does not decode the JWT: a role read out of a payload
 * nobody verified is a suggestion, and the server decides on every call anyway.
 * It is used only to choose which navigation to draw.
 */
import type { UserRole } from '../api/types'

const TOKEN_KEY = 'bloodlink.token'
const ROLE_KEY = 'bloodlink.role'

export interface Session {
  token: string
  role: UserRole
}

export function readSession(): Session | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    const role = localStorage.getItem(ROLE_KEY)
    if (!token || (role !== 'DONOR' && role !== 'REQUESTER')) {
      return null
    }
    return { token, role }
  } catch {
    // Private browsing with storage blocked. Not being able to remember a
    // session is survivable; crashing on load is not.
    return null
  }
}

export function writeSession(session: Session): void {
  try {
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(ROLE_KEY, session.role)
  } catch {
    // The session still works for this tab, held in memory by the provider.
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(ROLE_KEY)
  } catch {
    // Nothing to do: there was nothing readable to clear.
  }
}
