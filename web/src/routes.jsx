import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import PrivateRoute from './components/PrivateRoute'

// Cada página se carga solo cuando se visita
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const Home = lazy(() => import('./pages/Home'))
const NotFound = lazy(() => import('./pages/NotFound'))

export default function AppRoutes() {
  return (
    <Suspense fallback={<p>Cargando...</p>}>
      <Routes>
        {/* Públicas */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Requieren sesión */}
        <Route element={<PrivateRoute />}>
          <Route path="/" element={<Home />} />
        </Route>

        {/* Solo clientes */}
        <Route element={<PrivateRoute roles={['CLIENT']} />}>
        </Route>

        {/* Solo freelancers */}
        <Route element={<PrivateRoute roles={['FREELANCER']} />}>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
