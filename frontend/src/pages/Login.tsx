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
      await verifyCode(email, code)
      onLogin()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Ugyldig kode')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-summa-900 flex items-center justify-center">
      <div className="bg-summa-800 rounded-xl border border-summa-700 p-8 w-full max-w-md">
        <div className="text-center mb-8">
          <img src="/favicon.svg" alt="" className="w-12 h-12 mx-auto mb-4" />
          <h1 className="text-3xl font-bold text-summa-100">Summa Summarum</h1>
          <p className="text-summa-500 font-mono text-xs mt-2 tracking-wide uppercase">Logg inn med engangskode</p>
        </div>

        {error && (
          <div className="bg-red-900/30 text-red-300 border border-red-800 p-3 rounded-lg mb-4 text-sm">{error}</div>
        )}

        {step === 'email' ? (
          <form onSubmit={handleRequestCode} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-summa-400 mb-1">E-postadresse</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                className="w-full px-3 py-2 bg-summa-900 border border-summa-700 rounded-lg text-summa-100 placeholder-summa-600 focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="din@epost.no"
                required
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-2 rounded-lg hover:bg-summa-600 transition-colors disabled:opacity-50"
            >
              {loading ? 'Sender...' : 'Send engangskode'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyCode} className="space-y-4">
            <p className="text-sm text-summa-400">
              Engangskode sendt til <strong className="text-summa-100">{email}</strong>
            </p>
            <div>
              <label className="block text-sm font-medium text-summa-400 mb-1">Engangskode</label>
              <input
                type="text"
                value={code}
                onChange={e => setCode(e.target.value)}
                className="w-full px-3 py-2 bg-summa-900 border border-summa-700 rounded-lg text-summa-100 placeholder-summa-600 focus:ring-2 focus:ring-summa-500 focus:border-transparent text-center text-2xl tracking-widest font-mono"
                placeholder="123456"
                maxLength={6}
                required
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-2 rounded-lg hover:bg-summa-600 transition-colors disabled:opacity-50"
            >
              {loading ? 'Verifiserer...' : 'Logg inn'}
            </button>
            <button
              type="button"
              onClick={() => { setStep('email'); setCode(''); setError(null) }}
              className="w-full text-summa-500 hover:text-summa-400 text-sm"
            >
              Bruk annen e-post
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
