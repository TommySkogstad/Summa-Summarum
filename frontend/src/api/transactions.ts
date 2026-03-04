import { apiRequest, apiFormDataRequest } from './apiClient'

export interface ParsedLineItem {
  description?: string
  quantity?: string
  unitPrice?: string
  amount?: string
  vatRate?: string
  vatAmount?: string
}

export interface ParsedDocument {
  id: number
  totalAmount?: string
  currency?: string
  vatAmount?: string
  vatRate?: string
  invoiceDate?: string
  paymentDueDate?: string
  paymentReference?: string
  vendorName?: string
  vendorOrgNumber?: string
  invoiceNumber?: string
  confidence?: string
  status: 'SUCCESS' | 'FAILED' | 'UNSUPPORTED'
  errorMessage?: string
  lineItems: ParsedLineItem[]
}

export interface Attachment {
  id: number
  transactionId: number
  filename: string
  originalName: string
  mimeType: string
  createdAt: string
  parsedDocument?: ParsedDocument
}

export interface Transaction {
  id: number
  date: string
  type: 'INNTEKT' | 'UTGIFT'
  amount: string
  currency: string
  vatRate?: string
  vatAmount?: string
  exchangeRate?: string
  amountNok?: string
  description: string
  vendorName?: string
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

export async function createTransaction(data: Record<string, unknown>): Promise<Transaction> {
  return apiRequest<Transaction>('/transactions', { method: 'POST', body: data })
}

export async function updateTransaction(id: number, data: Record<string, unknown>): Promise<Transaction> {
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

export interface AttachmentWithTransaction {
  id: number
  transactionId: number
  filename: string
  originalName: string
  mimeType: string
  createdAt: string
  transactionDate: string
  transactionDescription: string
}

export interface AttachmentListResponse {
  attachments: AttachmentWithTransaction[]
  total: number
}

export async function getAllAttachments(): Promise<AttachmentListResponse> {
  return apiRequest<AttachmentListResponse>('/attachments')
}

export async function parseDocument(file: File): Promise<ParsedDocument> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFormDataRequest<ParsedDocument>('/parse-document', formData)
}

export interface ExchangeRateResponse {
  base: string
  target: string
  rate: string
  date: string
}

export async function getExchangeRate(currency: string, date: string): Promise<ExchangeRateResponse> {
  return apiRequest<ExchangeRateResponse>(`/exchange-rate?currency=${currency}&date=${date}`)
}
