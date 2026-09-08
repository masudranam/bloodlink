import { useMutation } from '@tanstack/react-query'
import { revealContact } from '../api/queries'
import { ErrorNote } from './States'

/**
 * The reveal.
 *
 * The only component in the client that reads a phone number, and the shape of
 * it is the point of SPEC-009. Three rules it exists to hold:
 *
 * <ul>
 *   <li><strong>Nothing is fetched on mount.</strong> This is a mutation, fired
 *       by a press. A `useQuery` here would write an audit row every time
 *       somebody opened a request, and the log would fill with reveals that
 *       never happened in any meaningful sense.</li>
 *   <li><strong>The button says what pressing it does.</strong> It is not a
 *       "show" toggle over a number the client already holds — before the press
 *       there is no number here to hide.</li>
 *   <li><strong>The recorded time is shown back.</strong> The person sees that
 *       the look was written down rather than being assured that it was.</li>
 * </ul>
 */
export default function ContactPanel({
  pledgeId,
  otherSide,
}: {
  pledgeId: number
  otherSide: string
}) {
  const reveal = useMutation({ mutationFn: () => revealContact(pledgeId) })

  if (reveal.data) {
    const { counterparty, revealedAt } = reveal.data
    return (
      <div className="contact contact--revealed">
        <p className="contact__name">
          {counterparty.fullName} <span className="contact__role">{counterparty.role}</span>
        </p>
        <p className="contact__phone">
          <a href={`tel:${counterparty.phone}`}>{counterparty.phone}</a>
        </p>
        <p className="contact__note">
          Recorded in the reveal log at {new Date(revealedAt).toLocaleString()}. They can see that
          you looked.
        </p>
      </div>
    )
  }

  return (
    <div className="contact">
      <p className="contact__note">
        The {otherSide}&apos;s phone number has not been sent to this device. Asking for it is
        recorded in the reveal log, and they can see that you looked.
      </p>
      <button
        type="button"
        className="button button--reveal"
        onClick={() => reveal.mutate()}
        disabled={reveal.isPending}
      >
        {reveal.isPending ? 'Revealing…' : `Reveal the ${otherSide}'s phone number`}
      </button>
      {reveal.error ? <ErrorNote error={reveal.error} /> : null}
    </div>
  )
}
