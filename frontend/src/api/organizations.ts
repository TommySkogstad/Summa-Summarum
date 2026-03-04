import { apiRequest } from './apiClient'

export interface Organization {
  id: number
  name: string
  orgNumber?: string
  mvaRegistered: boolean
  active: boolean
  createdAt: string
}

export async function getOrganizations(): Promise<Organization[]> {
  return apiRequest<Organization[]>('/organizations')
}

export async function createOrganization(data: { name: string; orgNumber?: string; mvaRegistered?: boolean }): Promise<Organization> {
  return apiRequest<Organization>('/organizations', { method: 'POST', body: data })
}

export async function updateOrganization(id: number, data: Partial<Organization>): Promise<Organization> {
  return apiRequest<Organization>(`/organizations/${id}`, { method: 'PUT', body: data })
}
