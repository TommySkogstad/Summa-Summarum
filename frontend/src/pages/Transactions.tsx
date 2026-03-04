import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getTransactions, deleteTransaction, TransactionFilters } from '../api/transactions'
import { getCategories } from '../api/categories'
import { formatCurrency, formatDate } from '../lib/formatters'

export function Transactions() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<TransactionFilters>({ page: 1, pageSize: 20 })

  const { data, isLoading } = useQuery({
    queryKey: ['transactions', filters],
    queryFn: () => getTransactions(filters),
  })

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })

  const totalPages = data ? Math.ceil(data.total / (filters.pageSize || 20)) : 0

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Transaksjoner</h1>
        <Link
          to="/transaksjoner/ny"
          className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
        >
          Ny transaksjon
        </Link>
      </div>

      {/* Filtre */}
      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          <input
            type="text"
            placeholder="Sok..."
            value={filters.search || ''}
            onChange={e => setFilters(prev => ({ ...prev, search: e.target.value, page: 1 }))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          />
          <select
            value={filters.type || ''}
            onChange={e => setFilters(prev => ({ ...prev, type: e.target.value || undefined, page: 1 }))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          >
            <option value="">Alle typer</option>
            <option value="INNTEKT">Inntekt</option>
            <option value="UTGIFT">Utgift</option>
          </select>
          <select
            value={filters.categoryId || ''}
            onChange={e => setFilters(prev => ({ ...prev, categoryId: e.target.value ? Number(e.target.value) : undefined, page: 1 }))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          >
            <option value="">Alle kategorier</option>
            {categories?.filter(c => c.active).map(c => (
              <option key={c.id} value={c.id}>{c.code} {c.name}</option>
            ))}
          </select>
          <input
            type="date"
            value={filters.dateFrom || ''}
            onChange={e => setFilters(prev => ({ ...prev, dateFrom: e.target.value || undefined, page: 1 }))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          />
          <input
            type="date"
            value={filters.dateTo || ''}
            onChange={e => setFilters(prev => ({ ...prev, dateTo: e.target.value || undefined, page: 1 }))}
            className="px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Tabell */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        {isLoading ? (
          <div className="p-6 text-gray-500 text-center">Laster transaksjoner...</div>
        ) : data?.transactions.length === 0 ? (
          <div className="p-6 text-gray-500 text-center">Ingen transaksjoner funnet</div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Dato</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Beskrivelse</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Kategori</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Belop</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Handlinger</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {data?.transactions.map(tx => (
                <tr key={tx.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm">{formatDate(tx.date)}</td>
                  <td className="px-4 py-3">
                    <Link to={`/transaksjoner/${tx.id}`} className="text-summa-600 hover:text-summa-800 font-medium">
                      {tx.description}
                    </Link>
                    {tx.vendorName && (
                      <span className="ml-2 text-xs text-gray-500">{tx.vendorName}</span>
                    )}
                    {tx.attachments.length > 0 && (
                      <span className="ml-2 text-xs text-gray-400">{tx.attachments.length} vedlegg</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    <span className="font-mono">{tx.categoryCode}</span> {tx.categoryName}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-1 rounded-full ${
                      tx.type === 'INNTEKT'
                        ? 'bg-green-100 text-green-700'
                        : 'bg-red-100 text-red-700'
                    }`}>
                      {tx.type === 'INNTEKT' ? 'Inntekt' : 'Utgift'}
                    </span>
                  </td>
                  <td className={`px-4 py-3 text-right font-medium ${
                    tx.type === 'INNTEKT' ? 'text-green-600' : 'text-red-600'
                  }`}>
                    {tx.type === 'INNTEKT' ? '+' : '-'}{formatCurrency(tx.amount)} {tx.currency && tx.currency !== 'NOK' ? tx.currency : ''}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/transaksjoner/${tx.id}`} className="text-sm text-summa-600 hover:text-summa-800 mr-3">
                      Rediger
                    </Link>
                    <button
                      onClick={() => { if (confirm('Slett transaksjon?')) deleteMutation.mutate(tx.id) }}
                      className="text-sm text-red-600 hover:text-red-800"
                    >
                      Slett
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Paginering */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
            <p className="text-sm text-gray-600">
              Viser {((filters.page || 1) - 1) * (filters.pageSize || 20) + 1}-{Math.min((filters.page || 1) * (filters.pageSize || 20), data?.total || 0)} av {data?.total}
            </p>
            <div className="flex gap-2">
              <button
                disabled={(filters.page || 1) <= 1}
                onClick={() => setFilters(prev => ({ ...prev, page: (prev.page || 1) - 1 }))}
                className="px-3 py-1 border rounded text-sm disabled:opacity-50"
              >
                Forrige
              </button>
              <button
                disabled={(filters.page || 1) >= totalPages}
                onClick={() => setFilters(prev => ({ ...prev, page: (prev.page || 1) + 1 }))}
                className="px-3 py-1 border rounded text-sm disabled:opacity-50"
              >
                Neste
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
