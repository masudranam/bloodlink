import { ApiError } from '../api/client'

/**
 * The three things every screen needs and none of them should improvise:
 * loading, failed, and nothing here.
 *
 * SPEC-009 AC-18 exists because a screen that renders empty on a failed call is
 * indistinguishable from one where the answer really is "nothing", and in this
 * application those two are very different.
 */

export function Loading({ what }: { what: string }) {
  return (
    <p className="state state--loading" role="status">
      Loading {what}…
    </p>
  )
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <p className="state state--empty">{children}</p>
}

/**
 * A failure, rendered from the server's own words.
 *
 * The `detail` of a `ProblemDetail` is written to be read by a person — "You may
 * donate again from 2026-09-09" rather than "conflict" — so it is shown as-is
 * instead of being translated into a message of the client's own invention.
 */
export function ErrorNote({ error }: { error: unknown }) {
  const message =
    error instanceof ApiError
      ? error.detail
      : error instanceof Error
        ? error.message
        : 'Something went wrong.'

  return (
    <p className="state state--error" role="alert">
      {message}
    </p>
  )
}

/** A field-level message from a 400's `errors` map. */
export function FieldError({ errors, field }: { errors: Record<string, string>; field: string }) {
  const message = errors[field]
  if (!message) {
    return null
  }
  return <span className="field__error">{message}</span>
}
