import type { UserRole } from '../api/types'
import Link from '../routing/Link'

/**
 * A screen for the other role.
 *
 * Shown instead of sending a request that could only come back 403. Being told
 * plainly which role a screen belongs to is more useful than a permission error,
 * and much more useful than a blank page (SPEC-009 AC-4).
 */
export default function WrongRoleScreen({
  needed,
  actual,
}: {
  needed: UserRole
  actual: UserRole
}) {
  return (
    <section className="card">
      <h2>Not a screen for your role</h2>
      <p>
        This page is for a {needed.toLowerCase()}, and you are signed in as a{' '}
        {actual.toLowerCase()}. Nothing was requested from the server.
      </p>
      <p>
        <Link to="/">Back to the feed</Link>.
      </p>
    </section>
  )
}
