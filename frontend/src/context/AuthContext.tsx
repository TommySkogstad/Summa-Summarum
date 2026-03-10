import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { getMe, switchOrg, logout as apiLogout, type AuthUser } from '../api/auth'
import { queryClient } from '../lib/queryClient'

interface AuthContextType {
  user: AuthUser | null
  organizations: AuthUser['organizations']
  activeOrg: AuthUser['organizations'][0] | null
  loading: boolean
  authenticated: boolean
  switchOrganization: (orgId: number) => Promise<void>
  refreshOrganizations: () => Promise<void>
  onLogin: () => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function getCsrfTokenFromCookie(): string {
  const match = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : ''
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  const fetchUser = useCallback(async () => {
    try {
      const me = await getMe()
      setUser(me)
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchUser()
  }, [fetchUser])

  const onLogin = useCallback(() => {
    fetchUser()
  }, [fetchUser])

  const switchOrganization = useCallback(async (orgId: number) => {
    const token = getCsrfTokenFromCookie()
    if (!token) return
    try {
      await switchOrg(orgId, token)
      queryClient.clear()
      await fetchUser()
    } catch (err) {
      console.error('Kunne ikke bytte organisasjon:', err)
    }
  }, [fetchUser])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
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

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
