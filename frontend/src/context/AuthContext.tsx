import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { getOrganizations, Organization } from '../api/organizations'
import { queryClient } from '../lib/queryClient'
import { setActiveOrgId, getActiveOrgId } from '../api/apiClient'

interface OrgContextType {
  organizations: Organization[]
  activeOrg: Organization | null
  loading: boolean
  switchOrganization: (orgId: number) => void
  refreshOrganizations: () => Promise<void>
}

const OrgContext = createContext<OrgContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [activeOrgId, setActiveOrgIdState] = useState<number | null>(getActiveOrgId())
  const [loading, setLoading] = useState(true)

  const refreshOrganizations = async () => {
    try {
      const orgs = await getOrganizations()
      setOrganizations(orgs)

      // Velg forste org hvis ingen er valgt, eller valgt org ikke finnes lenger
      if (orgs.length > 0) {
        const currentId = getActiveOrgId()
        if (!currentId || !orgs.find(o => o.id === currentId)) {
          setActiveOrgId(orgs[0].id)
          setActiveOrgIdState(orgs[0].id)
        }
      }
    } catch {
      // Ignore - kanskje backend ikke er klart enna
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refreshOrganizations()
  }, [])

  const switchOrganization = (orgId: number) => {
    setActiveOrgId(orgId)
    setActiveOrgIdState(orgId)
    queryClient.clear()
  }

  const activeOrg = organizations.find(o => o.id === activeOrgId) ?? null

  return (
    <OrgContext.Provider value={{ organizations, activeOrg, loading, switchOrganization, refreshOrganizations }}>
      {children}
    </OrgContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(OrgContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
