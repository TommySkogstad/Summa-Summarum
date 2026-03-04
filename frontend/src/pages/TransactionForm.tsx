import { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTransaction, createTransaction, updateTransaction, uploadAttachment, deleteAttachment, parseDocument, getExchangeRate, type ParsedDocument } from '../api/transactions'
import { getCategories } from '../api/categories'
import { useAuth } from '../context/AuthContext'

export function TransactionForm() {
  const { id } = useParams()
  const isEditing = !!id
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { activeOrg } = useAuth()

  const [formData, setFormData] = useState({
    date: new Date().toISOString().split('T')[0],
    type: 'UTGIFT',
    amount: '',
    currency: 'NOK',
    vatRate: '' as string,
    vatAmount: '' as string,
    exchangeRate: '' as string,
    amountNok: '' as string,
    description: '',
    vendorName: '',
    categoryId: 0,
  })
  const [error, setError] = useState<string | null>(null)
  const [pendingFiles, setPendingFiles] = useState<{ id: number; file: File; parsed?: ParsedDocument | null; parsing?: boolean }[]>([])
  const nextPendingId = useRef(0)
  const [fetchingRate, setFetchingRate] = useState(false)

  const isMvaRegistered = activeOrg?.mvaRegistered ?? false
  const isForeignCurrency = formData.currency !== 'NOK'

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
        currency: transaction.currency || 'NOK',
        vatRate: transaction.vatRate || '',
        vatAmount: transaction.vatAmount || '',
        exchangeRate: transaction.exchangeRate || '',
        amountNok: transaction.amountNok || '',
        description: transaction.description,
        vendorName: transaction.vendorName || '',
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

  // Auto-beregn MVA-belop nar sats eller belop endres
  useEffect(() => {
    if (formData.vatRate && formData.amount) {
      const rate = parseFloat(formData.vatRate)
      const amount = parseFloat(formData.amount)
      if (!isNaN(rate) && !isNaN(amount) && rate > 0) {
        const vatAmount = (amount * rate / (100 + rate)).toFixed(2)
        setFormData(prev => ({ ...prev, vatAmount }))
      }
    }
  }, [formData.vatRate, formData.amount])

  // Auto-hent valutakurs nar valuta eller dato endres
  useEffect(() => {
    if (!isForeignCurrency) {
      setFormData(prev => ({ ...prev, exchangeRate: '', amountNok: '' }))
      return
    }
    if (!formData.date || !formData.currency) return

    let cancelled = false
    setFetchingRate(true)

    getExchangeRate(formData.currency, formData.date)
      .then(res => {
        if (!cancelled) {
          setFormData(prev => {
            const newData = { ...prev, exchangeRate: res.rate }
            if (prev.amount) {
              newData.amountNok = (parseFloat(prev.amount) * parseFloat(res.rate)).toFixed(2)
            }
            return newData
          })
        }
      })
      .catch(() => {
        // Ignorer feil, brukeren kan skrive inn manuelt
      })
      .finally(() => {
        if (!cancelled) setFetchingRate(false)
      })

    return () => { cancelled = true }
  }, [formData.currency, formData.date, isForeignCurrency])

  // Auto-beregn NOK-belop nar kurs eller belop endres
  useEffect(() => {
    if (isForeignCurrency && formData.exchangeRate && formData.amount) {
      const rate = parseFloat(formData.exchangeRate)
      const amount = parseFloat(formData.amount)
      if (!isNaN(rate) && !isNaN(amount)) {
        setFormData(prev => ({ ...prev, amountNok: (amount * rate).toFixed(2) }))
      }
    }
  }, [formData.exchangeRate, formData.amount, isForeignCurrency])

  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: async (data) => {
      if (pendingFiles.length > 0) {
        for (const pf of pendingFiles) {
          await uploadAttachment(data.id, pf.file)
        }
      }
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['overview'] })
      navigate(`/transaksjoner/${data.id}`)
    },
    onError: (err: Error) => setError(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Record<string, unknown> }) => updateTransaction(id, data),
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

    // Bygg data med valgfrie felt
    const submitData = {
      ...formData,
      vatRate: formData.vatRate || undefined,
      vatAmount: formData.vatAmount || undefined,
      exchangeRate: formData.exchangeRate || undefined,
      amountNok: formData.amountNok || undefined,
      vendorName: formData.vendorName || undefined,
    }

    if (isEditing) {
      updateMutation.mutate({ id: Number(id), data: submitData })
    } else {
      createMutation.mutate(submitData)
    }
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (isEditing) {
      uploadMutation.mutate({ txId: Number(id), file })
    } else {
      const fileId = nextPendingId.current++
      const canParse = file.type.startsWith('image/') || file.type === 'application/pdf'
      setPendingFiles(prev => [...prev, { id: fileId, file, parsing: canParse }])

      if (canParse) {
        try {
          const parsed = await parseDocument(file)
          setPendingFiles(prev => prev.map(pf => pf.id === fileId ? { ...pf, parsed, parsing: false } : pf))
        } catch {
          setPendingFiles(prev => prev.map(pf => pf.id === fileId ? { ...pf, parsed: null, parsing: false } : pf))
        }
      }
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

  const applyParsedData = (parsed: ParsedDocument) => {
    // Bygg beskrivelse fra fakturanummer + linjer
    const parts: string[] = []
    if (parsed.invoiceNumber) parts.push(`Faktura ${parsed.invoiceNumber}`)
    if (parsed.lineItems.length > 0) {
      parts.push(parsed.lineItems.map(li => li.description).filter(Boolean).join(', '))
    }
    const description = parts.filter(Boolean).join(' - ')

    setFormData(prev => ({
      ...prev,
      ...(parsed.totalAmount ? { amount: parsed.totalAmount } : {}),
      ...(parsed.currency ? { currency: parsed.currency } : {}),
      ...(parsed.invoiceDate ? { date: parsed.invoiceDate } : {}),
      ...(parsed.vendorName ? { vendorName: parsed.vendorName } : {}),
      ...(parsed.vatRate ? { vatRate: parsed.vatRate } : {}),
      ...(parsed.vatAmount ? { vatAmount: parsed.vatAmount } : {}),
      ...(description ? { description } : {}),
    }))
  }

  const filteredCategories = categories?.filter(c => c.active && c.type === formData.type) || []

  const renderParsedInfo = (parsed: ParsedDocument) => (
    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-blue-900">
      {parsed.vendorName && (
        <div><span className="text-blue-600">Leverandor:</span> {parsed.vendorName}</div>
      )}
      {parsed.totalAmount && (
        <div><span className="text-blue-600">Belop:</span> {parsed.totalAmount} {parsed.currency || 'NOK'}</div>
      )}
      {parsed.invoiceDate && (
        <div><span className="text-blue-600">Dato:</span> {parsed.invoiceDate}</div>
      )}
      {parsed.invoiceNumber && (
        <div><span className="text-blue-600">Fakturanr:</span> {parsed.invoiceNumber}</div>
      )}
      {parsed.vatAmount && (
        <div><span className="text-blue-600">MVA:</span> {parsed.vatAmount} ({parsed.vatRate}%)</div>
      )}
      {parsed.paymentDueDate && (
        <div><span className="text-blue-600">Forfall:</span> {parsed.paymentDueDate}</div>
      )}
      {parsed.paymentReference && (
        <div><span className="text-blue-600">KID:</span> {parsed.paymentReference}</div>
      )}
      {parsed.vendorOrgNumber && (
        <div><span className="text-blue-600">Org.nr:</span> {parsed.vendorOrgNumber}</div>
      )}
    </div>
  )

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
              <label className="block text-sm font-medium text-gray-700 mb-1">Belop</label>
              <div className="flex gap-2">
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={formData.amount}
                  onChange={e => setFormData(prev => ({ ...prev, amount: e.target.value }))}
                  className="flex-1 px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                  placeholder="0.00"
                  required
                />
                <select
                  value={formData.currency}
                  onChange={e => setFormData(prev => ({ ...prev, currency: e.target.value }))}
                  className="w-24 px-2 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                >
                  <option value="NOK">NOK</option>
                  <option value="SEK">SEK</option>
                  <option value="DKK">DKK</option>
                  <option value="EUR">EUR</option>
                  <option value="USD">USD</option>
                  <option value="GBP">GBP</option>
                </select>
              </div>
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

          {/* Valutakurs-seksjon */}
          {isForeignCurrency && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
              <h3 className="text-sm font-medium text-amber-800 mb-3">Valutakurs ({formData.currency} til NOK)</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs text-amber-700 mb-1">
                    Kurs {fetchingRate && <span className="text-amber-500">(henter...)</span>}
                  </label>
                  <input
                    type="number"
                    step="0.000001"
                    min="0"
                    value={formData.exchangeRate}
                    onChange={e => setFormData(prev => ({ ...prev, exchangeRate: e.target.value }))}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent text-sm"
                    placeholder="F.eks. 11.45"
                  />
                </div>
                <div>
                  <label className="block text-xs text-amber-700 mb-1">Belop i NOK</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.amountNok}
                    onChange={e => setFormData(prev => ({ ...prev, amountNok: e.target.value }))}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent text-sm"
                    placeholder="0.00"
                  />
                </div>
              </div>
            </div>
          )}

          {/* MVA-seksjon */}
          {isMvaRegistered && (
            <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4">
              <h3 className="text-sm font-medium text-indigo-800 mb-3">MVA</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs text-indigo-700 mb-1">MVA-sats</label>
                  <select
                    value={formData.vatRate}
                    onChange={e => setFormData(prev => ({ ...prev, vatRate: e.target.value }))}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-sm"
                  >
                    <option value="">Ingen MVA</option>
                    <option value="25">25% (standard)</option>
                    <option value="15">15% (mat)</option>
                    <option value="12">12% (transport, kultur)</option>
                    <option value="0">0% (fritatt)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-indigo-700 mb-1">MVA-belop</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.vatAmount}
                    onChange={e => setFormData(prev => ({ ...prev, vatAmount: e.target.value }))}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-sm"
                    placeholder="Auto-beregnet"
                  />
                </div>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Leverandor / Mottaker</label>
              <input
                type="text"
                value={formData.vendorName}
                onChange={e => setFormData(prev => ({ ...prev, vendorName: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="Navn pa leverandor eller mottaker"
              />
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

        {/* Vedlegg */}
        <div className="mt-8 pt-6 border-t">
          <h2 className="text-lg font-semibold mb-4">Vedlegg (bilag)</h2>

          {isEditing && transaction?.attachments && transaction.attachments.length > 0 && (
            <div className="space-y-3 mb-4">
              {transaction.attachments.map(att => (
                <div key={att.id}>
                  <div className="flex items-center justify-between bg-gray-50 p-3 rounded-lg">
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
                  {att.parsedDocument && att.parsedDocument.status === 'SUCCESS' && (
                    <div className="mt-1 bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm">
                      <div className="flex justify-between items-start mb-2">
                        <span className="font-medium text-blue-800">Parset fra bilag</span>
                        <button
                          type="button"
                          onClick={() => applyParsedData(att.parsedDocument!)}
                          className="text-xs bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 transition-colors"
                        >
                          Bruk data
                        </button>
                      </div>
                      {renderParsedInfo(att.parsedDocument)}
                      {att.parsedDocument.lineItems.length > 0 && (
                        <div className="mt-2 border-t border-blue-200 pt-2">
                          <span className="text-blue-600 text-xs">Linjer:</span>
                          {att.parsedDocument.lineItems.map((item, idx) => (
                            <div key={idx} className="text-xs text-blue-800">
                              {item.description}{item.amount ? ` — ${item.amount} ${att.parsedDocument!.currency || 'NOK'}` : ''}{item.vatAmount ? ` (MVA: ${item.vatAmount})` : ''}
                            </div>
                          ))}
                        </div>
                      )}
                      {att.parsedDocument.confidence && (
                        <div className="mt-1 text-xs text-blue-500">
                          Sikkerhet: {Math.round(Number(att.parsedDocument.confidence) * 100)}%
                        </div>
                      )}
                    </div>
                  )}
                  {att.parsedDocument && att.parsedDocument.status === 'FAILED' && (
                    <div className="mt-1 bg-red-50 border border-red-200 rounded-lg p-2 text-xs text-red-700">
                      Parsing feilet: {att.parsedDocument.errorMessage || 'Ukjent feil'}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {!isEditing && pendingFiles.length > 0 && (
            <div className="space-y-3 mb-4">
              {pendingFiles.map((pf) => (
                <div key={pf.id}>
                  <div className="flex items-center justify-between bg-gray-50 p-3 rounded-lg">
                    <span className="text-gray-700">{pf.file.name}</span>
                    <button
                      type="button"
                      onClick={() => setPendingFiles(prev => prev.filter(f => f.id !== pf.id))}
                      className="text-sm text-red-600 hover:text-red-800"
                    >
                      Fjern
                    </button>
                  </div>
                  {pf.parsing && (
                    <div className="mt-1 bg-blue-50 border border-blue-200 rounded-lg p-2 text-xs text-blue-700">
                      Analyserer dokument...
                    </div>
                  )}
                  {pf.parsed && pf.parsed.status === 'SUCCESS' && (
                    <div className="mt-1 bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm">
                      <div className="flex justify-between items-start mb-2">
                        <span className="font-medium text-blue-800">Parset fra bilag</span>
                        <button
                          type="button"
                          onClick={() => applyParsedData(pf.parsed!)}
                          className="text-xs bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 transition-colors"
                        >
                          Bruk data
                        </button>
                      </div>
                      {renderParsedInfo(pf.parsed)}
                      {pf.parsed.lineItems.length > 0 && (
                        <div className="mt-2 border-t border-blue-200 pt-2">
                          <span className="text-blue-600 text-xs">Linjer:</span>
                          {pf.parsed.lineItems.map((item, idx) => (
                            <div key={idx} className="text-xs text-blue-800">
                              {item.description}{item.amount ? ` — ${item.amount} ${pf.parsed!.currency || 'NOK'}` : ''}{item.vatAmount ? ` (MVA: ${item.vatAmount})` : ''}
                            </div>
                          ))}
                        </div>
                      )}
                      {pf.parsed.confidence && (
                        <div className="mt-1 text-xs text-blue-500">
                          Sikkerhet: {Math.round(Number(pf.parsed.confidence) * 100)}%
                        </div>
                      )}
                    </div>
                  )}
                  {pf.parsed && pf.parsed.status === 'FAILED' && (
                    <div className="mt-1 bg-red-50 border border-red-200 rounded-lg p-2 text-xs text-red-700">
                      Parsing feilet: {pf.parsed.errorMessage || 'Ukjent feil'}
                    </div>
                  )}
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
      </div>
    </div>
  )
}
