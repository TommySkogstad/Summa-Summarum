import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { requestCode, verifyCode } from '../api/auth'
import { useAuth } from '../context/AuthContext'

export function Login() {
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [step, setStep] = useState<'email' | 'code'>('email')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const { onLogin, authenticated, loading: authLoading } = useAuth()
  const navigate = useNavigate()

  // Redirect til dashboard hvis allerede innlogget
  useEffect(() => {
    if (!authLoading && authenticated) {
      navigate('/', { replace: true })
    }
  }, [authenticated, authLoading, navigate])

  const handleRequestCode = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await requestCode(email)
      setStep('code')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Kunne ikke sende kode')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyCode = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const result = await verifyCode(email, code)
      onLogin(result.csrfToken)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Ugyldig kode')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-lg p-8 w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-summa-900">Summa Summarum</h1>
          <p className="text-gray-500 mt-2">Logg inn med engangskode</p>
        </div>

        {error && (
          <div className="bg-red-50 text-red-700 p-3 rounded-lg mb-4 text-sm">{error}</div>
        )}

        {step === 'email' ? (
          <form onSubmit={handleRequestCode} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">E-postadresse</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="din@epost.no"
                required
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-2 rounded-lg hover:bg-summa-800 transition-colors disabled:opacity-50"
            >
              {loading ? 'Sender...' : 'Send engangskode'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyCode} className="space-y-4">
            <p className="text-sm text-gray-600">
              Engangskode sendt til <strong>{email}</strong>
            </p>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Engangskode</label>
              <input
                type="text"
                value={code}
                onChange={e => setCode(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent text-center text-2xl tracking-widest"
                placeholder="123456"
                maxLength={6}
                required
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-2 rounded-lg hover:bg-summa-800 transition-colors disabled:opacity-50"
            >
              {loading ? 'Verifiserer...' : 'Logg inn'}
            </button>
            <button
              type="button"
              onClick={() => { setStep('email'); setCode(''); setError(null) }}
              className="w-full text-gray-500 hover:text-gray-700 text-sm"
            >
              Bruk annen e-post
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
