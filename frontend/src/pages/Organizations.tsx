import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getOrganizations, createOrganization, updateOrganization, Organization } from '../api/organizations'
import { useAuth } from '../context/AuthContext'

export function Organizations() {
  const queryClient = useQueryClient()
  const { refreshOrganizations, user } = useAuth()
  const isSuperAdmin = user?.role === 'SUPERADMIN'
  const [showForm, setShowForm] = useState(false)
  const [formData, setFormData] = useState({ name: '', orgNumber: '', mvaRegistered: false })

  const { data: organizations, isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: getOrganizations,
  })

  const createMutation = useMutation({
    mutationFn: createOrganization,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      refreshOrganizations()
      setShowForm(false)
      setFormData({ name: '', orgNumber: '', mvaRegistered: false })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Organization> }) => updateOrganization(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
      refreshOrganizations()
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate({
      name: formData.name,
      orgNumber: formData.orgNumber || undefined,
      mvaRegistered: formData.mvaRegistered,
    })
  }

  const toggleMva = (org: Organization) => {
    updateMutation.mutate({
      id: org.id,
      data: { mvaRegistered: !org.mvaRegistered },
    })
  }

  if (isLoading) {
    return <div className="text-gray-500">Laster organisasjoner...</div>
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Organisasjoner</h1>
        {isSuperAdmin && (
          <button
            onClick={() => setShowForm(true)}
            className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
          >
            Ny organisasjon
          </button>
        )}
      </div>

      {isSuperAdmin && showForm && (
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Ny organisasjon</h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Navn</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={e => setFormData(prev => ({ ...prev, name: e.target.value }))}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                  placeholder="Bedriftsnavn"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Org.nummer (valgfritt)</label>
                <input
                  type="text"
                  value={formData.orgNumber}
                  onChange={e => setFormData(prev => ({ ...prev, orgNumber: e.target.value }))}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                  placeholder="123 456 789"
                />
              </div>
              <div className="flex items-end gap-2">
                <button
                  type="submit"
                  className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
                >
                  Opprett
                </button>
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="text-gray-600 hover:text-gray-800 px-4 py-2"
                >
                  Avbryt
                </button>
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={formData.mvaRegistered}
                onChange={e => setFormData(prev => ({ ...prev, mvaRegistered: e.target.checked }))}
                className="rounded border-gray-300 text-summa-600 focus:ring-summa-500"
              />
              <span className="text-gray-700">MVA-registrert</span>
            </label>
          </form>
          {createMutation.error && (
            <p className="text-red-600 mt-2 text-sm">{createMutation.error.message}</p>
          )}
        </div>
      )}

      <div className="bg-white rounded-lg shadow">
        <div className="divide-y">
          {organizations?.map((org: Organization) => (
            <div key={org.id} className={`flex items-center justify-between p-4 ${!org.active ? 'opacity-50' : ''}`}>
              <div className="flex items-center gap-3">
                <div>
                  <span className="font-medium">{org.name}</span>
                  {org.orgNumber && (
                    <span className="ml-3 text-sm text-gray-500">Org.nr: {org.orgNumber}</span>
                  )}
                  {!org.active && (
                    <span className="ml-2 text-xs bg-red-100 text-red-600 px-2 py-0.5 rounded">Deaktivert</span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-3">
                {isSuperAdmin ? (
                  <label className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={org.mvaRegistered}
                      onChange={() => toggleMva(org)}
                      className="rounded border-gray-300 text-summa-600 focus:ring-summa-500"
                    />
                    <span className="text-gray-600">MVA</span>
                  </label>
                ) : (
                  org.mvaRegistered && (
                    <span className="text-sm text-gray-500">MVA-registrert</span>
                  )
                )}
              </div>
            </div>
          ))}
          {organizations?.length === 0 && (
            <div className="p-4 text-gray-500 text-center">Ingen organisasjoner funnet</div>
          )}
        </div>
      </div>
    </div>
  )
}
