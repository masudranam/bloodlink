import { useAuth } from '../auth/authContext'
import { useRouter } from '../routing/routerContext'
import Link from '../routing/Link'

/**
 * The shell: title, role-appropriate navigation, sign out.
 *
 * The navigation is built from the role, so a donor is never shown a link to a
 * requester screen. The route guard in App is what actually enforces it — a
 * hidden link is tidiness, not security, and the server decides regardless.
 */
export default function Layout({ children }: { children: React.ReactNode }) {
  const { session, signOut } = useAuth()
  const { path, navigate } = useRouter()

  const links =
    session?.role === 'DONOR'
      ? [
          { to: '/', label: 'Feed' },
          { to: '/pledges', label: 'My pledges' },
          { to: '/profile', label: 'My profile' },
          { to: '/reveals', label: 'Who saw my number' },
        ]
      : session?.role === 'REQUESTER'
        ? [
            { to: '/', label: 'Feed' },
            { to: '/requests/new', label: 'Raise a request' },
            { to: '/requests/mine', label: 'My requests' },
            { to: '/reveals', label: 'Who saw my number' },
          ]
        : []

  return (
    <main className="shell">
      <header className="shell__header">
        <div className="row row--spread">
          <div>
            <h1>
              <Link to="/">BloodLink</Link>
            </h1>
            <p className="shell__tagline">A privacy-first blood donor network for Bangladesh.</p>
          </div>
          {session ? (
            <button
              type="button"
              className="button button--quiet"
              onClick={() => {
                signOut()
                navigate('/login', { replace: true })
              }}
            >
              Sign out
            </button>
          ) : null}
        </div>

        {links.length > 0 ? (
          <nav className="nav">
            {links.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={path === link.to ? 'nav__link nav__link--current' : 'nav__link'}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        ) : null}
      </header>

      {children}

      <footer className="shell__footer">
        <p>
          A phone number appears in exactly one place in this app: after a pledge is accepted, to
          the two people in it, and the look is recorded.
        </p>
      </footer>
    </main>
  )
}
