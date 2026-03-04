import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getCategories, createCategory, updateCategory, deleteCategory, Category } from '../api/categories'

export function Categories() {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formData, setFormData] = useState({ code: '', name: '', type: 'UTGIFT' as Category['type'] })

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      resetForm()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Category> }) => updateCategory(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      resetForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
  })

  const resetForm = () => {
    setShowForm(false)
    setEditingId(null)
    setFormData({ code: '', name: '', type: 'UTGIFT' as Category['type'] })
  }

  const handleEdit = (cat: Category) => {
    setEditingId(cat.id)
    setFormData({ code: cat.code, name: cat.name, type: cat.type })
    setShowForm(true)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (editingId) {
      updateMutation.mutate({ id: editingId, data: formData })
    } else {
      createMutation.mutate(formData)
    }
  }

  const handleToggleActive = (cat: Category) => {
    updateMutation.mutate({ id: cat.id, data: { active: !cat.active } })
  }

  if (isLoading) {
    return <div className="text-gray-500">Laster kategorier...</div>
  }

  const inntekter = categories?.filter(c => c.type === 'INNTEKT') || []
  const utgifter = categories?.filter(c => c.type === 'UTGIFT') || []

  const renderCategoryRow = (cat: Category) => (
    <div key={cat.id} className={`flex items-center justify-between p-4 ${!cat.active ? 'opacity-50' : ''}`}>
      <div>
        <span className="font-mono text-sm text-gray-500 mr-3">{cat.code}</span>
        <span className="font-medium">{cat.name}</span>
        {cat.isDefault && <span className="ml-2 text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">Standard</span>}
        {!cat.active && <span className="ml-2 text-xs bg-red-100 text-red-600 px-2 py-0.5 rounded">Deaktivert</span>}
      </div>
      <div className="flex gap-2">
        <button onClick={() => handleToggleActive(cat)} className="text-sm text-gray-500 hover:text-gray-700">
          {cat.active ? 'Deaktiver' : 'Aktiver'}
        </button>
        <button onClick={() => handleEdit(cat)} className="text-sm text-summa-600 hover:text-summa-800">
          Rediger
        </button>
        {!cat.isDefault && (
          <button onClick={() => deleteMutation.mutate(cat.id)} className="text-sm text-red-600 hover:text-red-800">
            Slett
          </button>
        )}
      </div>
    </div>
  )

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Kontoplan</h1>
        <button
          onClick={() => { resetForm(); setShowForm(true) }}
          className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
        >
          Ny kategori
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">{editingId ? 'Rediger kategori' : 'Ny kategori'}</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Kode</label>
              <input
                type="text"
                value={formData.code}
                onChange={e => setFormData(prev => ({ ...prev, code: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="4000"
                maxLength={4}
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Navn</label>
              <input
                type="text"
                value={formData.name}
                onChange={e => setFormData(prev => ({ ...prev, name: e.target.value }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="Varekostnad"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
              <select
                value={formData.type}
                onChange={e => setFormData(prev => ({ ...prev, type: e.target.value as Category['type'] }))}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
              >
                <option value="INNTEKT">Inntekt</option>
                <option value="UTGIFT">Utgift</option>
              </select>
            </div>
            <div className="flex items-end gap-2">
              <button
                type="submit"
                className="bg-summa-700 text-white px-4 py-2 rounded-lg hover:bg-summa-800 transition-colors"
              >
                {editingId ? 'Oppdater' : 'Opprett'}
              </button>
              <button
                type="button"
                onClick={resetForm}
                className="text-gray-600 hover:text-gray-800 px-4 py-2"
              >
                Avbryt
              </button>
            </div>
          </form>
          {(createMutation.error || updateMutation.error) && (
            <p className="text-red-600 mt-2 text-sm">
              {(createMutation.error || updateMutation.error)?.message}
            </p>
          )}
        </div>
      )}

      <div className="bg-white rounded-lg shadow mb-6">
        <div className="p-4 border-b bg-green-50">
          <h2 className="text-lg font-semibold text-green-800">Inntekter</h2>
        </div>
        <div className="divide-y">
          {inntekter.map(renderCategoryRow)}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow">
        <div className="p-4 border-b bg-red-50">
          <h2 className="text-lg font-semibold text-red-800">Utgifter</h2>
        </div>
        <div className="divide-y">
          {utgifter.map(renderCategoryRow)}
        </div>
      </div>
    </div>
  )
}
