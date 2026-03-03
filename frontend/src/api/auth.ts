const API_BASE = '/api'

interface RequestCodeResponse {
  message: string
  devOtp?: string
  cooldownSeconds?: number
}

interface VerifyCodeResponse {
  success: boolean
  role?: string
}

interface CurrentUser {
  id: number
  email: string
  name: string
  role: 'ADMIN'
}

export async function requestCode(email: string): Promise<RequestCodeResponse> {
  const response = await fetch(`${API_BASE}/auth/request-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email })
  })

  if (!response.ok) {
    const error = await response.json()
    throw new Error(error.error || 'Kunne ikke sende kode')
  }

  return response.json()
}

export async function verifyCode(email: string, code: string): Promise<VerifyCodeResponse> {
  const response = await fetch(`${API_BASE}/auth/verify-code`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, code })
  })

  if (!response.ok) {
    const error = await response.json()
    throw new Error(error.error || 'Ugyldig kode')
  }

  return response.json()
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
  const response = await fetch(`${API_BASE}/auth/me`, {
    method: 'GET',
    credentials: 'include'
  })

  if (!response.ok) {
    if (response.status === 401) {
      return null
    }
    throw new Error('Kunne ikke hente brukerinfo')
  }

  return response.json()
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    credentials: 'include'
  })
}
