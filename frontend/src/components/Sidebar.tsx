import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function Sidebar() {
  const { user, organizations, activeOrg, switchOrganization, logout } = useAuth()

  const links = [
    { to: '/', label: 'Dashboard' },
    { to: '/transaksjoner', label: 'Transaksjoner' },
    { to: '/bilag', label: 'Bilag' },
    { to: '/kategorier', label: 'Kontoplan' },
    { to: '/rapporter', label: 'Rapporter' },
    { to: '/organisasjoner', label: 'Organisasjoner' },
  ]

  return (
    <aside className="fixed left-0 top-0 h-full w-64 bg-summa-900 text-white flex flex-col">
      <div className="p-6 border-b border-summa-700">
        <h1 className="text-xl font-bold">Summa Summarum</h1>
        {organizations.length > 1 ? (
          <select
            value={activeOrg?.id ?? ''}
            onChange={(e) => {
              const orgId = Number(e.target.value)
              if (orgId) switchOrganization(orgId)
            }}
            className="mt-2 w-full bg-summa-800 text-summa-200 text-sm rounded px-2 py-1 border border-summa-600 focus:outline-none focus:border-summa-400"
          >
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
        ) : (
          <p className="text-summa-300 text-sm mt-1">
            {activeOrg?.name || 'Regnskap'}
          </p>
        )}
      </div>

      <nav className="flex-1 py-4">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.to === '/'}
            className={({ isActive }) =>
              `block px-6 py-3 text-sm transition-colors ${
                isActive
                  ? 'bg-summa-700 text-white border-r-2 border-summa-300'
                  : 'text-summa-300 hover:bg-summa-800 hover:text-white'
              }`
            }
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      {user && (
        <div className="p-4 border-t border-summa-700">
          <p className="text-summa-300 text-sm truncate">{user.name}</p>
          <p className="text-summa-500 text-xs truncate">{user.email}</p>
          <button
            onClick={logout}
            className="mt-2 text-summa-400 hover:text-white text-sm transition-colors"
          >
            Logg ut
          </button>
        </div>
      )}
    </aside>
  )
}
