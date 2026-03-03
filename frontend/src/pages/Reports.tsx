import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getMonthlyReport, getCategoryReport, getYearlyReport } from '../api/reports'
import { formatCurrency } from '../lib/formatters'

type Tab = 'monthly' | 'categories' | 'yearly'

export function Reports() {
  const currentYear = new Date().getFullYear()
  const [tab, setTab] = useState<Tab>('monthly')
  const [year, setYear] = useState(currentYear)
  const [month, setMonth] = useState<number | undefined>(undefined)

  const { data: monthlyData, isLoading: monthlyLoading } = useQuery({
    queryKey: ['monthly-report', year],
    queryFn: () => getMonthlyReport(year),
    enabled: tab === 'monthly',
  })

  const { data: categoryData, isLoading: categoryLoading } = useQuery({
    queryKey: ['category-report', year, month],
    queryFn: () => getCategoryReport(year, month),
    enabled: tab === 'categories',
  })

  const { data: yearlyData, isLoading: yearlyLoading } = useQuery({
    queryKey: ['yearly-report'],
    queryFn: getYearlyReport,
    enabled: tab === 'yearly',
  })

  const tabs: { id: Tab; label: string }[] = [
    { id: 'monthly', label: 'Per maned' },
    { id: 'categories', label: 'Per kategori' },
    { id: 'yearly', label: 'Per ar' },
  ]

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Rapporter</h1>

      {/* Faner */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg mb-6 w-fit">
        {tabs.map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              tab === t.id
                ? 'bg-white text-summa-700 shadow'
                : 'text-gray-600 hover:text-gray-800'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* År-velger */}
      {(tab === 'monthly' || tab === 'categories') && (
        <div className="flex gap-4 items-center mb-6">
          <label className="text-sm font-medium text-gray-700">Ar:</label>
          <select
            value={year}
            onChange={e => setYear(Number(e.target.value))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          >
            {Array.from({ length: 5 }, (_, i) => currentYear - i).map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
          {tab === 'categories' && (
            <>
              <label className="text-sm font-medium text-gray-700 ml-4">Maned:</label>
              <select
                value={month || ''}
                onChange={e => setMonth(e.target.value ? Number(e.target.value) : undefined)}
                className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
              >
                <option value="">Hele aret</option>
                {['Januar','Februar','Mars','April','Mai','Juni','Juli','August','September','Oktober','November','Desember'].map((name, i) => (
                  <option key={i} value={i + 1}>{name}</option>
                ))}
              </select>
            </>
          )}
        </div>
      )}

      {/* Manedlig rapport */}
      {tab === 'monthly' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          {monthlyLoading ? (
            <div className="p-6 text-gray-500 text-center">Laster rapport...</div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Maned</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Inntekter</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Utgifter</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Resultat</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {monthlyData?.months.map(row => {
                  const hasData = parseFloat(row.inntekter) > 0 || parseFloat(row.utgifter) > 0
                  const resultatPositive = parseFloat(row.resultat) >= 0
                  return (
                    <tr key={row.month} className={hasData ? '' : 'text-gray-400'}>
                      <td className="px-4 py-3 font-medium">{row.monthName}</td>
                      <td className="px-4 py-3 text-right text-green-600">{formatCurrency(row.inntekter)}</td>
                      <td className="px-4 py-3 text-right text-red-600">{formatCurrency(row.utgifter)}</td>
                      <td className={`px-4 py-3 text-right font-semibold ${resultatPositive ? 'text-green-600' : 'text-red-600'}`}>
                        {formatCurrency(row.resultat)}
                      </td>
                    </tr>
                  )
                })}
                {/* Total-rad */}
                {monthlyData && (
                  <tr className="bg-gray-50 font-bold">
                    <td className="px-4 py-3">Totalt</td>
                    <td className="px-4 py-3 text-right text-green-600">
                      {formatCurrency(monthlyData.months.reduce((sum, m) => sum + parseFloat(m.inntekter), 0))}
                    </td>
                    <td className="px-4 py-3 text-right text-red-600">
                      {formatCurrency(monthlyData.months.reduce((sum, m) => sum + parseFloat(m.utgifter), 0))}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {(() => {
                        const total = monthlyData.months.reduce((sum, m) => sum + parseFloat(m.resultat), 0)
                        return <span className={total >= 0 ? 'text-green-600' : 'text-red-600'}>{formatCurrency(total)}</span>
                      })()}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Kategorirapport */}
      {tab === 'categories' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          {categoryLoading ? (
            <div className="p-6 text-gray-500 text-center">Laster rapport...</div>
          ) : categoryData?.categories.length === 0 ? (
            <div className="p-6 text-gray-500 text-center">Ingen transaksjoner i valgt periode</div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Kode</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Kategori</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Antall</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {categoryData?.categories.map(row => (
                  <tr key={row.categoryId}>
                    <td className="px-4 py-3 font-mono text-sm">{row.categoryCode}</td>
                    <td className="px-4 py-3 font-medium">{row.categoryName}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-1 rounded-full ${
                        row.type === 'INNTEKT' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                      }`}>
                        {row.type === 'INNTEKT' ? 'Inntekt' : 'Utgift'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">{row.count}</td>
                    <td className={`px-4 py-3 text-right font-semibold ${
                      row.type === 'INNTEKT' ? 'text-green-600' : 'text-red-600'
                    }`}>
                      {formatCurrency(row.total)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Arsrapport */}
      {tab === 'yearly' && (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          {yearlyLoading ? (
            <div className="p-6 text-gray-500 text-center">Laster rapport...</div>
          ) : (
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ar</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Inntekter</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Utgifter</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Resultat</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {yearlyData?.years.map(row => {
                  const resultatPositive = parseFloat(row.resultat) >= 0
                  return (
                    <tr key={row.year}>
                      <td className="px-4 py-3 font-medium">{row.year}</td>
                      <td className="px-4 py-3 text-right text-green-600">{formatCurrency(row.inntekter)}</td>
                      <td className="px-4 py-3 text-right text-red-600">{formatCurrency(row.utgifter)}</td>
                      <td className={`px-4 py-3 text-right font-semibold ${resultatPositive ? 'text-green-600' : 'text-red-600'}`}>
                        {formatCurrency(row.resultat)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
