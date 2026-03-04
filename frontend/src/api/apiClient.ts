const API_BASE = '/api'

const ORG_STORAGE_KEY = 'summa_active_org_id'

export function setActiveOrgId(orgId: number) {
  localStorage.setItem(ORG_STORAGE_KEY, String(orgId))
}

export function getActiveOrgId(): number | null {
  const stored = localStorage.getItem(ORG_STORAGE_KEY)
  return stored ? Number(stored) : null
}

function buildHeaders(
  contentType?: string
): HeadersInit {
  const headers: Record<string, string> = {}

  if (contentType) {
    headers['Content-Type'] = contentType
  }

  const orgId = getActiveOrgId()
  if (orgId) {
    headers['X-Organization-Id'] = String(orgId)
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

  const headers = buildHeaders(body ? contentType : undefined)

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
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

  const orgId = getActiveOrgId()
  if (orgId) {
    headers['X-Organization-Id'] = String(orgId)
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers,
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
