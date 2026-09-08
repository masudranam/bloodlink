/**
 * The API's response shapes, written out by hand.
 *
 * These types are the client's half of the privacy rule. `RequestSummary`,
 * `DonorMatch`, `PledgeSummary` and `RevealEntry` declare no `phone` field, so a
 * component cannot render one even if the server were changed to start sending
 * it — the property would not exist as far as TypeScript is concerned.
 *
 * `Counterparty` is the only type here with a phone number, and `ContactPanel`
 * is the only component that reads it.
 */

export type BloodGroup = 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | 'O+' | 'O-'

export const BLOOD_GROUPS: BloodGroup[] = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']

export type UserRole = 'DONOR' | 'REQUESTER'

export type RequestStatus = 'OPEN' | 'PLEDGED' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED'

export const REQUEST_STATUSES: RequestStatus[] = [
  'OPEN',
  'PLEDGED',
  'FULFILLED',
  'CANCELLED',
  'EXPIRED',
]

export type PledgeStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ThanaSummary {
  id: number
  name: string
  district: string
}

export interface HospitalSummary {
  id: number
  name: string
  thana: string
}

export interface RequesterSummary {
  id: number
  fullName: string
}

/** A blood request. No phone number: SPEC-006 AC-13. */
export interface RequestSummary {
  id: number
  patientBloodGroup: BloodGroup
  hospital: HospitalSummary
  unitsNeeded: number
  neededBy: string
  status: RequestStatus
  note: string | null
  requester: RequesterSummary
  createdAt: string
}

/** A donor who could serve a request. No phone number: SPEC-007 AC-14. */
export interface DonorMatch {
  donorId: number
  fullName: string
  bloodGroup: BloodGroup
  thana: ThanaSummary
  distanceKm: number
  lastDonationDate: string | null
  nextEligibleDate: string | null
}

/**
 * A donor's own profile.
 *
 * `isEligible`, `nextEligibleDate` and `donationIntervalDays` are all computed by
 * the server on every read. The client displays them and never derives them: a
 * `lastDonationDate + 90` anywhere in this codebase would be pillar 2 broken on
 * the client while the server stayed correct.
 */
export interface DonorProfile {
  id: number
  bloodGroup: BloodGroup
  thana: ThanaSummary
  lastDonationDate: string | null
  available: boolean
  isEligible: boolean
  nextEligibleDate: string | null
  donationIntervalDays: number
}

export interface PledgeDonorSummary {
  donorId: number
  fullName: string
  bloodGroup: BloodGroup
  thana: ThanaSummary
}

/** A pledge, to either side. No phone number: SPEC-008 AC-19. */
export interface PledgeSummary {
  id: number
  requestId: number
  patientBloodGroup: BloodGroup
  hospital: HospitalSummary
  donor: PledgeDonorSummary
  status: PledgeStatus
  pledgedAt: string
  decidedAt: string | null
}

/**
 * The other party to an accepted pledge.
 *
 * The only type in the client that carries a phone number, reachable only from
 * the one endpoint that writes an audit row for handing it over.
 */
export interface Counterparty {
  fullName: string
  role: UserRole
  phone: string
}

export interface Contact {
  pledgeId: number
  requestId: number
  counterparty: Counterparty
  revealedAt: string
}

export interface ViewerSummary {
  fullName: string
  role: UserRole
}

/** One entry in the log of who has seen your number. Carries no number itself. */
export interface RevealEntry {
  id: number
  requestId: number
  pledgeId: number
  viewer: ViewerSummary
  revealedAt: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  role: UserRole
}

export interface MeResponse {
  id: number
  fullName: string
  role: UserRole
}

export interface CreateRequestBody {
  patientBloodGroup: BloodGroup
  hospitalId: number
  unitsNeeded: number
  neededBy: string
  note?: string
}

export interface ProfileBody {
  bloodGroup: BloodGroup
  thanaId: number
  lastDonationDate: string | null
  available: boolean
}
