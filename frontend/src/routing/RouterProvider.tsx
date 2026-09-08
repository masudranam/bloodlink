import { useCallback, useEffect, useMemo, useState } from 'react'
import { matchRoute } from './routes'
import { RouterContext } from './routerContext'
import type { RouterState } from './routerContext'

/**
 * The History API, wrapped.
 *
 * `pushState` for navigation, one `popstate` listener so the browser's back
 * button works, and `matchRoute` to decide what to draw. Vite serves index.html
 * for unknown paths in development, so a pasted deep link reaches the app rather
 * than a 404 from the dev server.
 */
export default function RouterProvider({ children }: { children: React.ReactNode }) {
  const [path, setPath] = useState(() => window.location.pathname)

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const navigate = useCallback((to: string, options?: { replace?: boolean }) => {
    if (options?.replace) {
      window.history.replaceState({}, '', to)
    } else {
      window.history.pushState({}, '', to)
    }
    // The pathname, not the whole target. Storing `to` verbatim meant a
    // navigation to '/login?next=%2F' was matched as the single segment
    // 'login?next=%2F', which matches no route and rendered "not a page" to
    // every signed-out visitor. The query string is read from
    // window.location.search by the one screen that needs it.
    setPath(new URL(to, window.location.origin).pathname)
    window.scrollTo(0, 0)
  }, [])

  const value = useMemo<RouterState>(
    () => ({ path, match: matchRoute(path), navigate }),
    [path, navigate],
  )

  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>
}
