import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

// Protege las rutas hijas. Con roles, además exige que el usuario tenga uno de ellos.
export default function PrivateRoute({ roles }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    // Se guarda la ruta pedida para volver a ella después del login
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (roles && !roles.includes(user.rol)) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
