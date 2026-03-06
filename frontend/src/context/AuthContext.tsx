import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { getMe, switchOrg, logout as apiLogout, type AuthUser } from '../api/auth'
import { queryClient } from '../lib/queryClient'
import { setCsrfToken } from '../api/apiClient'

interface AuthContextType {
  user: AuthUser | null
  organizations: AuthUser['organizations']
  activeOrg: AuthUser['organizations'][0] | null
  loading: boolean
  authenticated: boolean
  switchOrganization: (orgId: number) => Promise<void>
  refreshOrganizations: () => Promise<void>
  onLogin: (csrfToken?: string) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const fetchUser = useCallback(async () => {
    try {
      const me = await getMe()
      setUser(me)
      if (me.csrfToken) {
        setCsrfToken(me.csrfToken)
      }
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchUser()
  }, [fetchUser])

  const onLogin = useCallback((csrfToken?: string) => {
    if (csrfToken) setCsrfToken(csrfToken)
    fetchUser()
  }, [fetchUser])

  const switchOrganization = useCallback(async (orgId: number) => {
    const token = getCsrfTokenFromStorage()
    if (!token) return
    try {
      const result = await switchOrg(orgId, token)
      if (result.csrfToken) setCsrfToken(result.csrfToken)
      queryClient.clear()
      await fetchUser()
    } catch (err) {
      console.error('Kunne ikke bytte organisasjon:', err)
    }
  }, [fetchUser])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
    setCsrfToken('')
    queryClient.clear()
  }, [])

  const refreshOrganizations = useCallback(async () => {
    await fetchUser()
  }, [fetchUser])

  const organizations = user?.organizations ?? []
  const activeOrg = organizations.find(o => o.id === user?.activeOrgId) ?? null

  return (
    <AuthContext.Provider value={{
      user,
      organizations,
      activeOrg,
      loading,
      authenticated: !!user,
      switchOrganization,
      refreshOrganizations,
      onLogin,
      logout,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}

// Intern hjelpefunksjon
function getCsrfTokenFromStorage(): string | null {
  return sessionStorage.getItem('csrf_token')
}
