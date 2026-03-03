import { useState, useEffect, useRef } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { requestCode, verifyCode, getCurrentUser } from '../api/auth'
import { useAuth } from '../context/AuthContext'

export function Login() {
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [step, setStep] = useState<'email' | 'code'>('email')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [devOtp, setDevOtp] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)
  const cooldownRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname

  useEffect(() => {
    if (cooldown > 0) {
      cooldownRef.current = setInterval(() => {
        setCooldown((prev) => {
          if (prev <= 1) {
            if (cooldownRef.current) clearInterval(cooldownRef.current)
            return 0
          }
          return prev - 1
        })
      }, 1000)
    }
    return () => {
      if (cooldownRef.current) clearInterval(cooldownRef.current)
    }
  }, [cooldown])

  const handleResendCode = async () => {
    setError(null)
    setLoading(true)
    try {
      const response = await requestCode(email)
      if (response.devOtp) setDevOtp(response.devOtp)
      if (response.cooldownSeconds) setCooldown(response.cooldownSeconds)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Noe gikk galt')
    } finally {
      setLoading(false)
    }
  }

  const handleRequestCode = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const response = await requestCode(email)
      if (response.devOtp) setDevOtp(response.devOtp)
      if (response.cooldownSeconds) setCooldown(response.cooldownSeconds)
      setStep('code')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Noe gikk galt')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyCode = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const response = await verifyCode(email, code)
      if (response.success) {
        const user = await getCurrentUser()
        if (user) {
          login(user)
          navigate(from || '/', { replace: true })
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ugyldig kode')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-lg p-8 max-w-md w-full">
        <h1 className="text-3xl font-bold text-summa-900 mb-2">
          Summa Summarum
        </h1>
        <p className="text-gray-600 mb-8">
          {step === 'email' ? 'Logg inn med e-post' : 'Skriv inn koden'}
        </p>

        {error && (
          <div className="bg-red-50 text-red-700 p-3 rounded-lg mb-6">
            {error}
          </div>
        )}

        {devOtp && step === 'code' && (
          <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 p-3 rounded-lg mb-6">
            <strong>Dev-modus:</strong> Koden er <code className="bg-yellow-100 px-2 py-1 rounded">{devOtp}</code>
          </div>
        )}

        {step === 'email' ? (
          <form onSubmit={handleRequestCode}>
            <div className="mb-6">
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                E-postadresse
              </label>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent"
                placeholder="din@epost.no"
                required
                disabled={loading}
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-3 px-4 rounded-lg font-medium hover:bg-summa-800 transition-colors disabled:opacity-50"
            >
              {loading ? 'Sender...' : 'Send kode'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyCode}>
            <div className="mb-4">
              <p className="text-sm text-gray-600 mb-4">
                Hvis e-postadressen er registrert, har vi sendt en kode til <strong>{email}</strong>
              </p>
              <label htmlFor="code" className="block text-sm font-medium text-gray-700 mb-2">
                Engangskode
              </label>
              <input
                type="text"
                id="code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-summa-500 focus:border-transparent text-center text-2xl tracking-widest"
                placeholder="123456"
                maxLength={6}
                required
                disabled={loading}
                autoFocus
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-summa-700 text-white py-3 px-4 rounded-lg font-medium hover:bg-summa-800 transition-colors disabled:opacity-50 mb-4"
            >
              {loading ? 'Verifiserer...' : 'Logg inn'}
            </button>
            <button
              type="button"
              onClick={handleResendCode}
              disabled={cooldown > 0 || loading}
              className="w-full text-summa-600 hover:text-summa-800 py-2 font-medium disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {cooldown > 0 ? `Vent ${cooldown} sekunder...` : 'Send kode pa nytt'}
            </button>
            <button
              type="button"
              onClick={() => {
                setStep('email')
                setCode('')
                setDevOtp(null)
                setCooldown(0)
              }}
              className="w-full text-gray-600 hover:text-gray-800 py-2"
            >
              Bruk annen e-post
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
