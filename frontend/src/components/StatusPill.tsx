import type { PledgeStatus, RequestStatus } from '../api/types'

/**
 * A status, coloured by what it means rather than by which enum it came from.
 *
 * Open and pending are live, accepted is good news, and the terminal ones are
 * grey because there is nothing left to do about them.
 */
export default function StatusPill({ status }: { status: RequestStatus | PledgeStatus }) {
  const tone =
    status === 'OPEN' || status === 'PENDING'
      ? 'live'
      : status === 'PLEDGED' || status === 'ACCEPTED'
        ? 'good'
        : status === 'FULFILLED'
          ? 'done'
          : 'closed'

  return <span className={`pill pill--${tone}`}>{status}</span>
}
