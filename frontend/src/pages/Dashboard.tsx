import { useQuery } from '@tanstack/react-query'
import { getOverview } from '../api/reports'
import { formatCurrency, formatDate } from '../lib/formatters'
import { Link } from 'react-router-dom'

export function Dashboard() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['overview'],
    queryFn: getOverview,
  })

  if (isLoading) {
    return <div className="text-gray-500">Laster oversikt...</div>
  }

  if (error) {
    return <div className="text-red-600">Feil ved lasting av oversikt</div>
  }

  if (!data) return null

  const resultatPositive = parseFloat(data.resultat) >= 0

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-summa-900">Dashboard</h1>
        <Link
          to="/transaksjoner/ny"
          className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
        >
          Ny transaksjon
        </Link>
      </div>

      {/* Nøkkeltall */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow p-6">
          <p className="text-sm text-gray-600 mb-1">Inntekter (YTD)</p>
          <p className="text-2xl font-bold text-green-600">{formatCurrency(data.totalInntekter)}</p>
        </div>
        <div className="bg-white rounded-lg shadow p-6">
          <p className="text-sm text-gray-600 mb-1">Utgifter (YTD)</p>
          <p className="text-2xl font-bold text-red-600">{formatCurrency(data.totalUtgifter)}</p>
        </div>
        <div className="bg-white rounded-lg shadow p-6">
          <p className="text-sm text-gray-600 mb-1">Resultat (YTD)</p>
          <p className={`text-2xl font-bold ${resultatPositive ? 'text-green-600' : 'text-red-600'}`}>
            {formatCurrency(data.resultat)}
          </p>
        </div>
        <div className="bg-white rounded-lg shadow p-6">
          <p className="text-sm text-gray-600 mb-1">Transaksjoner (YTD)</p>
          <p className="text-2xl font-bold text-summa-700">{data.antallTransaksjoner}</p>
        </div>
      </div>

      {/* Siste transaksjoner */}
      <div className="bg-white rounded-lg shadow">
        <div className="p-6 border-b">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-semibold">Siste transaksjoner</h2>
            <Link to="/transaksjoner" className="text-summa-600 hover:text-summa-800 text-sm">
              Se alle
            </Link>
          </div>
        </div>
        <div className="divide-y">
          {data.sisteTransaksjoner.length === 0 ? (
            <p className="p-6 text-gray-500 text-center">Ingen transaksjoner enna</p>
          ) : (
            data.sisteTransaksjoner.map((tx) => (
              <Link key={tx.id} to={`/transaksjoner/${tx.id}`} className="flex items-center justify-between p-4 hover:bg-gray-50">
                <div>
                  <p className="font-medium text-gray-900">{tx.description}</p>
                  <p className="text-sm text-gray-500">
                    {tx.categoryCode} {tx.categoryName} &middot; {formatDate(tx.date)}
                  </p>
                </div>
                <p className={`font-semibold ${tx.type === 'INNTEKT' ? 'text-green-600' : 'text-red-600'}`}>
                  {tx.type === 'INNTEKT' ? '+' : '-'}{formatCurrency(tx.amount)}
                </p>
              </Link>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
