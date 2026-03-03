import { apiRequest, apiFormDataRequest } from './apiClient'

export interface Attachment {
  id: number
  transactionId: number
  filename: string
  originalName: string
  mimeType: string
  createdAt: string
}

export interface Transaction {
  id: number
  date: string
  type: 'INNTEKT' | 'UTGIFT'
  amount: string
  description: string
  categoryId: number
  categoryCode?: string
  categoryName?: string
  createdBy?: number
  createdAt: string
  updatedAt: string
  attachments: Attachment[]
}

export interface TransactionListResponse {
  transactions: Transaction[]
  total: number
  page: number
  pageSize: number
}

export interface TransactionFilters {
  page?: number
  pageSize?: number
  type?: string
  categoryId?: number
  search?: string
  dateFrom?: string
  dateTo?: string
}

export async function getTransactions(filters: TransactionFilters = {}): Promise<TransactionListResponse> {
  const params = new URLSearchParams()
  if (filters.page) params.set('page', String(filters.page))
  if (filters.pageSize) params.set('pageSize', String(filters.pageSize))
  if (filters.type) params.set('type', filters.type)
  if (filters.categoryId) params.set('categoryId', String(filters.categoryId))
  if (filters.search) params.set('search', filters.search)
  if (filters.dateFrom) params.set('dateFrom', filters.dateFrom)
  if (filters.dateTo) params.set('dateTo', filters.dateTo)

  const query = params.toString()
  return apiRequest<TransactionListResponse>(`/transactions${query ? '?' + query : ''}`)
}

export async function getTransaction(id: number): Promise<Transaction> {
  return apiRequest<Transaction>(`/transactions/${id}`)
}

export async function createTransaction(data: {
  date: string
  type: string
  amount: string
  description: string
  categoryId: number
}): Promise<Transaction> {
  return apiRequest<Transaction>('/transactions', { method: 'POST', body: data })
}

export async function updateTransaction(id: number, data: Partial<{
  date: string
  type: string
  amount: string
  description: string
  categoryId: number
}>): Promise<Transaction> {
  return apiRequest<Transaction>(`/transactions/${id}`, { method: 'PUT', body: data })
}

export async function deleteTransaction(id: number): Promise<void> {
  return apiRequest(`/transactions/${id}`, { method: 'DELETE' })
}

export async function uploadAttachment(transactionId: number, file: File): Promise<Attachment> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFormDataRequest<Attachment>(`/transactions/${transactionId}/attachments`, formData)
}

export async function deleteAttachment(transactionId: number, attachmentId: number): Promise<void> {
  return apiRequest(`/transactions/${transactionId}/attachments/${attachmentId}`, { method: 'DELETE' })
}
