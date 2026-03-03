import { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTransaction, createTransaction, updateTransaction, uploadAttachment, deleteAttachment } from '../api/transactions'
import { getCategories } from '../api/categories'

export function TransactionForm() {
  const { id } = useParams()
  const isEditing = !!id
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [formData, setFormData] = useState({
    date: new Date().toISOString().split('T')[0],
    type: 'UTGIFT',
    amount: '',
    description: '',
    categoryId: 0,
  })
  const [error, setError] = useState<string | null>(null)

  const { data: transaction } = useQuery({
    queryKey: ['transaction', id],
    queryFn: () => getTransaction(Number(id)),
    enabled: isEditing,
  })

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  useEffect(() => {
    if (transaction) {
      setFormData({
        date: transaction.date,
        type: transaction.type,
        amount: transaction.amount,
        description: transaction.description,
        categoryId: transaction.categoryId,
      })
    }
  }, [transaction])

  // Sett default kategori nar kategorier lastes
  useEffect(() => {
    if (categories && categories.length > 0 && !formData.categoryId) {
      const activeCategories = categories.filter(c => c.active && c.type === formData.type)
      if (activeCategories.length > 0) {
        setFormData(prev => ({ ...prev, categoryId: activeCategories[0].id }))
      }
    }
  }, [categories, formData.type, formData.categoryId])

  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['overview'] })
      navigate(`/transaksjoner/${data.id}`)
    },
    onError: (err: Error) => setError(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: typeof formData }) => updateTransaction(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['transaction', id] })
      queryClient.invalidateQueries({ queryKey: ['overview'] })
    },
    onError: (err: Error) => setError(err.message),
  })

  const uploadMutation = useMutation({
    mutationFn: ({ txId, file }: { txId: number; file: File }) => uploadAttachment(txId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transaction', id] })
    },
    onError: (err: Error) => setError(err.message),
  })

  const deleteAttachmentMutation = useMutation({
    mutationFn: ({ txId, attId }: { txId: number; attId: number }) => deleteAttachment(txId, attId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transaction', id] })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!formData.amount || !formData.description || !formData.categoryId) {
      setError('Fyll ut alle felt')
      return
    }

    if (isEditing) {
      updateMutation.mutate({ id: Number(id), data: formData })
    } else {
      createMutation.mutate(formData)
    }
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file && id) {
      uploadMutation.mutate({ txId: Number(id), file })
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const handleTypeChange = (type: string) => {
    setFormData(prev => {
      const newData = { ...prev, type }
      // Oppdater kategori til forste aktive av ny type
      if (categories) {
        const activeCategories = categories.filter(c => c.active && c.type === type)
        if (activeCategories.length > 0) {
          newData.categoryId = activeCategories[0].id
        }
      }
      return newData
    })
  }

  const filteredCategories = categories?.filter(c => c.active && c.type === formData.type) || []

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          {isEditing ? 'Rediger transaksjon' : 'Ny transaksjon'}
        </h1>
        <button
          onClick={() => navigate('/transaksjoner')}
          className="text-gray-600 hover:text-gray-800"
        >
          Tilbake
        </button>
      </div>

      {error && (
        <div className="bg-red-50 text-red-700 p-3 rounded-lg mb-6">{error}</div>
      )}

      <div className="bg-white rounded-lg shadow p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Dato</label>
              <input
                type="date"
                value={formData.date}
                onChange={e => setFormData(prev => ({ ...prev, date: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
              <div className="flex gap-4">
                <label className="flex items-center">
                  <input
                    type="radio"
                    value="INNTEKT"
                    checked={formData.type === 'INNTEKT'}
                    onChange={() => handleTypeChange('INNTEKT')}
                    className="mr-2"
                  />
                  <span className="text-green-700">Inntekt</span>
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    value="UTGIFT"
                    checked={formData.type === 'UTGIFT'}
                    onChange={() => handleTypeChange('UTGIFT')}
                    className="mr-2"
                  />
                  <span className="text-red-700">Utgift</span>
                </label>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Belop (NOK)</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={formData.amount}
                onChange={e => setFormData(prev => ({ ...prev, amount: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="0.00"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Kategori</label>
              <select
                value={formData.categoryId}
                onChange={e => setFormData(prev => ({ ...prev, categoryId: Number(e.target.value) }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                required
              >
                <option value={0} disabled>Velg kategori</option>
                {filteredCategories.map(c => (
                  <option key={c.id} value={c.id}>{c.code} {c.name}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Beskrivelse</label>
            <input
              type="text"
              value={formData.description}
              onChange={e => setFormData(prev => ({ ...prev, description: e.target.value }))}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
              placeholder="Beskriv transaksjonen..."
              required
            />
          </div>

          <div className="flex gap-3">
            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="bg-summa-700 text-white px-6 py-2 rounded-lg hover:bg-summa-800 transition-colors disabled:opacity-50"
            >
              {createMutation.isPending || updateMutation.isPending ? 'Lagrer...' : isEditing ? 'Oppdater' : 'Opprett'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/transaksjoner')}
              className="text-gray-600 hover:text-gray-800 px-6 py-2"
            >
              Avbryt
            </button>
          </div>
        </form>

        {/* Vedlegg (kun ved redigering) */}
        {isEditing && (
          <div className="mt-8 pt-6 border-t">
            <h2 className="text-lg font-semibold mb-4">Vedlegg (bilag)</h2>

            {transaction?.attachments && transaction.attachments.length > 0 && (
              <div className="space-y-2 mb-4">
                {transaction.attachments.map(att => (
                  <div key={att.id} className="flex items-center justify-between bg-gray-50 p-3 rounded-lg">
                    <a
                      href={`/api/transactions/${id}/attachments/${att.id}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-summa-600 hover:text-summa-800"
                    >
                      {att.originalName}
                    </a>
                    <button
                      onClick={() => deleteAttachmentMutation.mutate({ txId: Number(id), attId: att.id })}
                      className="text-sm text-red-600 hover:text-red-800"
                    >
                      Slett
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div>
              <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileUpload}
                className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:bg-summa-100 file:text-summa-700 hover:file:bg-summa-200"
                accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
              />
              {uploadMutation.isPending && <p className="text-sm text-gray-500 mt-1">Laster opp...</p>}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
