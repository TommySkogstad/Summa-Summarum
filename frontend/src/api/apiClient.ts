const API_BASE = '/api'

function getCsrfTokenFromCookie(): string {
  const match = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : ''
}

function buildHeaders(
  contentType?: string,
  method?: string
): HeadersInit {
  const headers: Record<string, string> = {}

  if (contentType) {
    headers['Content-Type'] = contentType
  }

  // Legg til CSRF-token pa muterende operasjoner (leses fra cookie)
  if (method && method !== 'GET' && method !== 'HEAD') {
    const csrfToken = getCsrfTokenFromCookie()
    if (csrfToken) {
      headers['X-CSRF-Token'] = csrfToken
    }
  }

  return headers
}

export async function apiRequest<T>(
  endpoint: string,
  options: {
    method?: string
    body?: unknown
    contentType?: string
  } = {}
): Promise<T> {
  const { method = 'GET', body, contentType = 'application/json' } = options

  const headers = buildHeaders(body ? contentType : undefined, method)

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined
  })

  if (response.status === 401) {
    window.location.href = '/login'
    throw new Error('Ikke innlogget')
  }

  if (!response.ok) {
    let errorMessage: string
    try {
      const error = await response.json()
      errorMessage = error.error || `Feil (${response.status})`
    } catch {
      errorMessage = `Serverfeil (${response.status})`
    }
    throw new Error(errorMessage)
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  return JSON.parse(text)
}

export async function apiFormDataRequest<T>(
  endpoint: string,
  formData: FormData,
  method: string = 'POST'
): Promise<T> {
  const headers: Record<string, string> = {}

  // CSRF-token pa muterende operasjoner (leses fra cookie)
  if (method !== 'GET' && method !== 'HEAD') {
    const csrfToken = getCsrfTokenFromCookie()
    if (csrfToken) {
      headers['X-CSRF-Token'] = csrfToken
    }
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
    credentials: 'include',
    body: formData
  })

  if (response.status === 401) {
    window.location.href = '/login'
    throw new Error('Ikke innlogget')
  }

  if (!response.ok) {
    let errorMessage: string
    try {
      const error = await response.json()
      errorMessage = error.error || `Feil (${response.status})`
    } catch {
      errorMessage = `Serverfeil (${response.status})`
    }
    throw new Error(errorMessage)
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  return JSON.parse(text)
}

export { API_BASE }
