const API_BASE = '/api'

export interface AuthUser {
  id: number
  email: string
  name: string
  role: string
  activeOrgId: number | null
  organizations: { id: number; name: string; mvaRegistered: boolean }[]
  csrfToken?: string
}

export async function requestCode(email: string): Promise<{ message: string }> {
  const res = await fetch(`${API_BASE}/auth/request-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'Feil ved sending av kode' }))
    throw new Error(err.error)
  }
  return res.json()
}

export async function verifyCode(email: string, code: string): Promise<{ message: string; csrfToken?: string }> {
  const res = await fetch(`${API_BASE}/auth/verify-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, code }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'Ugyldig kode' }))
    throw new Error(err.error)
  }
  return res.json()
}

export async function getMe(): Promise<AuthUser> {
  const res = await fetch(`${API_BASE}/auth/me`, {
    credentials: 'include',
  })
  if (!res.ok) throw new Error('Ikke innlogget')
  return res.json()
}

export async function switchOrg(orgId: number, csrfToken: string): Promise<{ message: string; csrfToken?: string }> {
  const res = await fetch(`${API_BASE}/auth/switch-org/${orgId}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-CSRF-Token': csrfToken },
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: 'Kunne ikke bytte organisasjon' }))
    throw new Error(err.error)
  }
  return res.json()
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
  })
}
