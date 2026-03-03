const API_BASE = '/api'

export function getCsrfToken(): string | null {
  const match = document.cookie.match(/csrf_token=([^;]+)/)
  return match ? match[1] : null
}

function buildHeaders(
  method: string,
  contentType?: string
): HeadersInit {
  const headers: Record<string, string> = {}

  if (contentType) {
    headers['Content-Type'] = contentType
  }

  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method.toUpperCase())) {
    const csrfToken = getCsrfToken()
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

  const headers = buildHeaders(method, body ? contentType : undefined)

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined
  })

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

  const csrfToken = getCsrfToken()
  if (csrfToken) {
    headers['X-CSRF-Token'] = csrfToken
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
    credentials: 'include',
    body: formData
  })

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
