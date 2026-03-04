import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getAllAttachments, deleteAttachment, deleteTransaction, AttachmentWithTransaction } from '../api/transactions'
import { formatDate } from '../lib/formatters'

export function Attachments() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['attachments'],
    queryFn: getAllAttachments,
  })

  const deleteAttachmentMutation = useMutation({
    mutationFn: ({ txId, attId }: { txId: number; attId: number }) => deleteAttachment(txId, attId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })

  const deleteTransactionMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attachments'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['overview'] })
    },
  })

  const filtered = data?.attachments.filter((att) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      att.originalName.toLowerCase().includes(q) ||
      att.transactionDescription.toLowerCase().includes(q)
    )
  }) ?? []

  const isImage = (mimeType: string) => mimeType.startsWith('image/')

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Bilag og vedlegg</h1>
        <span className="text-sm text-gray-500">{data?.total ?? 0} totalt</span>
      </div>

      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <input
          type="text"
          placeholder="Sok i filnavn eller beskrivelse..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
        />
      </div>

      {isLoading ? (
        <div className="bg-white rounded-lg shadow p-6 text-gray-500 text-center">
          Laster vedlegg...
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-6 text-gray-500 text-center">
          Ingen vedlegg funnet
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((att) => (
            <AttachmentCard
              key={att.id}
              attachment={att}
              isImage={isImage(att.mimeType)}
              onDeleteAttachment={() => {
                if (confirm(`Slett vedlegg "${att.originalName}"?`))
                  deleteAttachmentMutation.mutate({ txId: att.transactionId, attId: att.id })
              }}
              onDeleteTransaction={() => {
                if (confirm(`Slett transaksjonen "${att.transactionDescription}" og alle dens vedlegg?`))
                  deleteTransactionMutation.mutate(att.transactionId)
              }}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function AttachmentCard({
  attachment: att,
  isImage,
  onDeleteAttachment,
  onDeleteTransaction,
}: {
  attachment: AttachmentWithTransaction
  isImage: boolean
  onDeleteAttachment: () => void
  onDeleteTransaction: () => void
}) {
  return (
    <div className="bg-white rounded-lg shadow overflow-hidden">
      {isImage ? (
        <a
          href={`/api/transactions/${att.transactionId}/attachments/${att.id}`}
          target="_blank"
          rel="noopener noreferrer"
          className="block"
        >
          <img
            src={`/api/transactions/${att.transactionId}/attachments/${att.id}`}
            alt={att.originalName}
            className="w-full h-48 object-cover bg-gray-100"
          />
        </a>
      ) : (
        <a
          href={`/api/transactions/${att.transactionId}/attachments/${att.id}`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center justify-center h-48 bg-gray-50 text-gray-400"
        >
          <div className="text-center">
            <div className="text-4xl mb-2">
              {att.mimeType.includes('pdf') ? '\u{1F4C4}' : '\u{1F4CE}'}
            </div>
            <div className="text-sm">{att.mimeType.split('/')[1]?.toUpperCase() ?? 'FIL'}</div>
          </div>
        </a>
      )}
      <div className="p-4">
        <a
          href={`/api/transactions/${att.transactionId}/attachments/${att.id}`}
          target="_blank"
          rel="noopener noreferrer"
          className="font-medium text-summa-600 hover:text-summa-800 text-sm block truncate"
          title={att.originalName}
        >
          {att.originalName}
        </a>
        <div className="mt-2 text-xs text-gray-500">
          <Link
            to={`/transaksjoner/${att.transactionId}`}
            className="text-summa-600 hover:text-summa-800"
          >
            {att.transactionDescription}
          </Link>
          <span className="mx-1">&middot;</span>
          {formatDate(att.transactionDate)}
        </div>
        <div className="mt-3 flex gap-2">
          <button
            onClick={onDeleteAttachment}
            className="text-xs text-red-600 hover:text-red-800"
          >
            Slett vedlegg
          </button>
          <span className="text-gray-300">|</span>
          <button
            onClick={onDeleteTransaction}
            className="text-xs text-red-600 hover:text-red-800"
          >
            Slett transaksjon
          </button>
        </div>
      </div>
    </div>
  )
}
