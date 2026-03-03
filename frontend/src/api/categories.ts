import { apiRequest } from './apiClient'

export interface Category {
  id: number
  code: string
  name: string
  type: 'INNTEKT' | 'UTGIFT'
  active: boolean
  isDefault: boolean
}

export async function getCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/categories')
}

export async function createCategory(data: { code: string; name: string; type: string }): Promise<Category> {
  return apiRequest<Category>('/categories', { method: 'POST', body: data })
}

export async function updateCategory(id: number, data: Partial<Category>): Promise<Category> {
  return apiRequest<Category>(`/categories/${id}`, { method: 'PUT', body: data })
}

export async function deleteCategory(id: number): Promise<void> {
  return apiRequest(`/categories/${id}`, { method: 'DELETE' })
}
