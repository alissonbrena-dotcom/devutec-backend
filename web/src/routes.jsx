import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import PrivateRoute from './components/PrivateRoute'

// Cada página se carga solo cuando se visita
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const Home = lazy(() => import('./pages/Home'))
const NotFound = lazy(() => import('./pages/NotFound'))
const ProjectsExplore = lazy(() => import('./pages/projects/ProjectsExplore'))
const ProjectDetail = lazy(() => import('./pages/projects/ProjectDetail'))
const ProjectCreate = lazy(() => import('./pages/projects/ProjectCreate'))
const ProjectEdit = lazy(() => import('./pages/projects/ProjectEdit'))
const MyProjects = lazy(() => import('./pages/projects/MyProjects'))

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
          <Route path="/projects" element={<ProjectsExplore />} />
          <Route path="/projects/:id" element={<ProjectDetail />} />
        </Route>

        {/* Solo clientes */}
        <Route element={<PrivateRoute roles={['CLIENT']} />}>
          <Route path="/projects/new" element={<ProjectCreate />} />
          <Route path="/my-projects" element={<MyProjects />} />
        </Route>

        {/* Dueño del proyecto o admin (el backend valida que sea el dueño) */}
        <Route element={<PrivateRoute roles={['CLIENT', 'ADMIN']} />}>
          <Route path="/projects/:id/edit" element={<ProjectEdit />} />
        </Route>

        {/* Solo freelancers */}
        <Route element={<PrivateRoute roles={['FREELANCER']} />}>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
