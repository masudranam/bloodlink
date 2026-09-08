/**
 * Query keys and the calls behind them.
 *
 * The keys mirror the endpoints, so a mutation can invalidate exactly what it
 * changed: accepting a pledge invalidates that request's pledges and the request
 * itself, and nothing else.
 *
 * The contact endpoint is deliberately **not** here as a query. It is a mutation
 * (`revealContact`), because calling it writes an audit row — a `useQuery` on it
 * would fire on mount and record a reveal for a page nobody meaningfully looked
 * at. SPEC-009 AC-11.
 */
import { api } from './client'
import type {
  Contact,
  CreateRequestBody,
  DonorMatch,
  DonorProfile,
  HospitalSummary,
  LoginResponse,
  MeResponse,
  PageResponse,
  PledgeSummary,
  ProfileBody,
  RequestStatus,
  RequestSummary,
  RevealEntry,
  ThanaSummary,
  UserRole,
} from './types'

export const keys = {
  me: ['me'] as const,
  thanas: ['thanas'] as const,
  hospitals: ['hospitals'] as const,
  feed: (status: RequestStatus, page: number) => ['requests', status, page] as const,
  request: (id: number) => ['requests', id] as const,
  donors: (id: number, radiusKm: number, page: number) =>
    ['requests', id, 'donors', radiusKm, page] as const,
  pledgesOnRequest: (id: number, page: number) => ['requests', id, 'pledges', page] as const,
  myPledges: (page: number) => ['donors', 'me', 'pledges', page] as const,
  myProfile: ['donors', 'me'] as const,
  myReveals: (page: number) => ['me', 'reveals', page] as const,
}

export function login(phone: string, password: string): Promise<LoginResponse> {
  return api.post<LoginResponse>('/api/auth/login', { phone, password })
}

export function registerUser(
  fullName: string,
  phone: string,
  password: string,
  role: UserRole,
): Promise<unknown> {
  return api.post('/api/auth/register', { fullName, phone, password, role })
}

export function fetchMe(): Promise<MeResponse> {
  return api.get<MeResponse>('/api/auth/me')
}

export function fetchThanas(): Promise<ThanaSummary[]> {
  return api.get<ThanaSummary[]>('/api/thanas')
}

export function fetchHospitals(): Promise<HospitalSummary[]> {
  return api.get<HospitalSummary[]>('/api/hospitals')
}

export function fetchFeed(
  status: RequestStatus,
  page: number,
): Promise<PageResponse<RequestSummary>> {
  return api.get<PageResponse<RequestSummary>>(
    `/api/requests?status=${status}&page=${page}&size=10`,
  )
}

export function fetchRequest(id: number): Promise<RequestSummary> {
  return api.get<RequestSummary>(`/api/requests/${id}`)
}

export function createRequest(body: CreateRequestBody): Promise<RequestSummary> {
  return api.post<RequestSummary>('/api/requests', body)
}

export function cancelRequest(id: number): Promise<RequestSummary> {
  return api.post<RequestSummary>(`/api/requests/${id}/cancel`)
}

export function fulfilRequest(id: number): Promise<RequestSummary> {
  return api.post<RequestSummary>(`/api/requests/${id}/fulfil`)
}

export function fetchDonors(
  id: number,
  radiusKm: number,
  page: number,
): Promise<PageResponse<DonorMatch>> {
  return api.get<PageResponse<DonorMatch>>(
    `/api/requests/${id}/donors?radiusKm=${radiusKm}&page=${page}&size=10`,
  )
}

export function fetchPledgesOnRequest(
  id: number,
  page: number,
): Promise<PageResponse<PledgeSummary>> {
  return api.get<PageResponse<PledgeSummary>>(`/api/requests/${id}/pledges?page=${page}&size=10`)
}

export function fetchMyPledges(page: number): Promise<PageResponse<PledgeSummary>> {
  return api.get<PageResponse<PledgeSummary>>(`/api/donors/me/pledges?page=${page}&size=10`)
}

export function pledge(requestId: number): Promise<PledgeSummary> {
  return api.post<PledgeSummary>(`/api/requests/${requestId}/pledges`)
}

export function acceptPledge(id: number): Promise<PledgeSummary> {
  return api.post<PledgeSummary>(`/api/pledges/${id}/accept`)
}

export function declinePledge(id: number): Promise<PledgeSummary> {
  return api.post<PledgeSummary>(`/api/pledges/${id}/decline`)
}

export function withdrawPledge(id: number): Promise<PledgeSummary> {
  return api.post<PledgeSummary>(`/api/pledges/${id}/withdraw`)
}

/**
 * The reveal. A mutation, not a query, because it writes an audit row.
 */
export function revealContact(pledgeId: number): Promise<Contact> {
  return api.get<Contact>(`/api/pledges/${pledgeId}/contact`)
}

export function fetchMyProfile(): Promise<DonorProfile> {
  return api.get<DonorProfile>('/api/donors/me')
}

export function createProfile(body: ProfileBody): Promise<DonorProfile> {
  return api.post<DonorProfile>('/api/donors/me', body)
}

export function replaceProfile(body: ProfileBody): Promise<DonorProfile> {
  return api.put<DonorProfile>('/api/donors/me', body)
}

export function fetchMyReveals(page: number): Promise<PageResponse<RevealEntry>> {
  return api.get<PageResponse<RevealEntry>>(`/api/me/reveals?page=${page}&size=10`)
}
