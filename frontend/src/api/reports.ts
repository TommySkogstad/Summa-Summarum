import { apiRequest } from './apiClient'
import type { Transaction } from './transactions'

export interface OverviewResponse {
  totalInntekter: string
  totalUtgifter: string
  resultat: string
  antallTransaksjoner: number
  sisteTransaksjoner: Transaction[]
}

export interface MonthlyReportRow {
  month: number
  monthName: string
  inntekter: string
  utgifter: string
  resultat: string
}

export interface MonthlyReportResponse {
  year: number
  months: MonthlyReportRow[]
}

export interface CategoryReportRow {
  categoryId: number
  categoryCode: string
  categoryName: string
  type: string
  total: string
  count: number
}

export interface CategoryReportResponse {
  year: number
  month?: number
  categories: CategoryReportRow[]
}

export interface YearlyReportRow {
  year: number
  inntekter: string
  utgifter: string
  resultat: string
}

export interface YearlyReportResponse {
  years: YearlyReportRow[]
}

export interface MvaReportResponse {
  year: number
  month?: number
  utgaaendeMva: string
  inngaaendeMva: string
  mvaGrunnlagUtgaaende: string
  mvaGrunnlagInngaaende: string
  nettoBetaling: string
}

export async function getOverview(): Promise<OverviewResponse> {
  return apiRequest<OverviewResponse>('/reports/overview')
}

export async function getMonthlyReport(year: number): Promise<MonthlyReportResponse> {
  return apiRequest<MonthlyReportResponse>(`/reports/monthly?year=${year}`)
}

export async function getCategoryReport(year: number, month?: number): Promise<CategoryReportResponse> {
  const params = `year=${year}${month ? `&month=${month}` : ''}`
  return apiRequest<CategoryReportResponse>(`/reports/categories?${params}`)
}

export async function getYearlyReport(): Promise<YearlyReportResponse> {
  return apiRequest<YearlyReportResponse>('/reports/yearly')
}

export async function getMvaReport(year: number, month?: number): Promise<MvaReportResponse> {
  const params = `year=${year}${month ? `&month=${month}` : ''}`
  return apiRequest<MvaReportResponse>(`/reports/mva?${params}`)
}
