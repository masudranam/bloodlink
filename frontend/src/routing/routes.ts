/**
 * The route table, and matching a path against it.
 *
 * Sixty lines instead of react-router-dom, because the project's rule is that no
 * dependency arrives without being asked for and SPEC-009 could not ask. The
 * shape is deliberately minimal: static segments plus one `:id`. If routing ever
 * needs nested layouts or query-string state, that is the moment to swap in the
 * real thing, and nothing outside this folder depends on how it works.
 */
import type { UserRole } from '../api/types'

export type ScreenName =
  | 'login'
  | 'register'
  | 'feed'
  | 'newRequest'
  | 'myRequests'
  | 'request'
  | 'myPledges'
  | 'profile'
  | 'reveals'
  | 'notFound'

export interface RouteDefinition {
  pattern: string
  screen: ScreenName
  /** Undefined means any signed-in user; 'public' means no token needed. */
  role?: UserRole | 'public'
}

export const ROUTES: RouteDefinition[] = [
  { pattern: '/login', screen: 'login', role: 'public' },
  { pattern: '/register', screen: 'register', role: 'public' },
  { pattern: '/', screen: 'feed' },
  { pattern: '/requests/new', screen: 'newRequest', role: 'REQUESTER' },
  { pattern: '/requests/mine', screen: 'myRequests', role: 'REQUESTER' },
  { pattern: '/requests/:id', screen: 'request' },
  { pattern: '/pledges', screen: 'myPledges', role: 'DONOR' },
  { pattern: '/profile', screen: 'profile', role: 'DONOR' },
  { pattern: '/reveals', screen: 'reveals' },
]

export interface Match {
  screen: ScreenName
  role?: UserRole | 'public'
  params: Record<string, string>
}

const NOT_FOUND: Match = { screen: 'notFound', params: {} }

export function matchRoute(path: string): Match {
  const parts = split(path)

  // Static patterns win over ':id' ones because they are listed first, which is
  // why /requests/new is not read as a request with the id "new".
  for (const route of ROUTES) {
    const expected = split(route.pattern)
    if (expected.length !== parts.length) {
      continue
    }

    const params: Record<string, string> = {}
    let matched = true
    for (let index = 0; index < expected.length; index += 1) {
      const segment = expected[index]
      const actual = parts[index]
      if (segment.startsWith(':')) {
        params[segment.slice(1)] = actual
      } else if (segment !== actual) {
        matched = false
        break
      }
    }

    if (matched) {
      return { screen: route.screen, role: route.role, params }
    }
  }

  return NOT_FOUND
}

function split(path: string): string[] {
  return path.split('/').filter((segment) => segment.length > 0)
}

export function requestPath(id: number): string {
  return `/requests/${id}`
}
