import { createContext, useContext } from 'react'
import type { Match } from './routes'

export interface RouterState {
  path: string
  match: Match
  navigate: (to: string, options?: { replace?: boolean }) => void
}

export const RouterContext = createContext<RouterState | null>(null)

export function useRouter(): RouterState {
  const state = useContext(RouterContext)
  if (!state) {
    throw new Error('useRouter used outside RouterProvider')
  }
  return state
}

export function useNavigate(): RouterState['navigate'] {
  return useRouter().navigate
}
