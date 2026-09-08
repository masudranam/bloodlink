/**
 * The only place in the client that calls `fetch`.
 *
 * Everything goes through here so that there is one place the token is attached,
 * one place a `401` is handled, and one place the server's RFC 7807
 * `ProblemDetail` is turned into something a screen can render. A second `fetch`
 * somewhere would be a second set of answers to all three.
 */

/** A failure the server described. */
export class ApiError extends Error {
  status: number
  detail: string
  /** Field name to message, from the server's `errors` map on a 400. */
  errors: Record<string, string>
  /** The problem type slug, e.g. `donor-not-eligible`. */
  problem: string

  constructor(status: number, detail: string, errors: Record<string, string>, problem: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.errors = errors
    this.problem = problem
  }
}

let token: string | null = null
let onUnauthorized: (() => void) | null = null

export function setToken(next: string | null): void {
  token = next
}

/**
 * Registers what to do when the server says the token is no good.
 *
 * The client wrapper cannot navigate on its own, so `AuthProvider` hands it a
 * callback that clears the session and returns to the login screen.
 */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

interface ProblemBody {
  detail?: string
  title?: string
  type?: string
  errors?: Record<string, string>
}

function problemSlug(type: string | undefined): string {
  if (!type) {
    return ''
  }
  const parts = type.split('/')
  return parts[parts.length - 1] ?? ''
}

async function toError(response: Response): Promise<ApiError> {
  let body: ProblemBody = {}
  try {
    body = (await response.json()) as ProblemBody
  } catch {
    // A body that is not JSON, or no body at all: a 401 from the filter chain
    // before a controller was reached, for instance.
  }

  const detail =
    body.detail ?? body.title ?? `The server returned ${response.status} ${response.statusText}`

  return new ApiError(response.status, detail, body.errors ?? {}, problemSlug(body.type))
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401) {
    // Handled centrally: an expired token must not leave one screen showing an
    // error while the rest of the app still believes it is logged in.
    const error = await toError(response)
    if (onUnauthorized) {
      onUnauthorized()
    }
    throw error
  }

  if (!response.ok) {
    throw await toError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
