import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function Sidebar() {
  const { user, logout } = useAuth()

  const links = [
    { to: '/', label: 'Dashboard' },
    { to: '/transaksjoner', label: 'Transaksjoner' },
    { to: '/kategorier', label: 'Kontoplan' },
    { to: '/rapporter', label: 'Rapporter' },
  ]

  return (
    <aside className="fixed left-0 top-0 h-full w-64 bg-summa-900 text-white flex flex-col">
      <div className="p-6 border-b border-summa-700">
        <h1 className="text-xl font-bold">Summa Summarum</h1>
        <p className="text-summa-300 text-sm mt-1">Regnskap</p>
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

      <div className="p-4 border-t border-summa-700">
        {user && (
          <div className="mb-3">
            <p className="text-sm font-medium truncate">{user.name}</p>
            <p className="text-xs text-summa-400 truncate">{user.email}</p>
          </div>
        )}
        <button
          onClick={logout}
          className="w-full text-left text-sm text-summa-400 hover:text-white transition-colors"
        >
          Logg ut
        </button>
      </div>
    </aside>
  )
}
