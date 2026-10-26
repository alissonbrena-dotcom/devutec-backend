# DevUTEC — Web

Frontend web de DevUTEC, hecho con React y Vite.

## Cómo correrlo

```bash
cd web
cp .env.example .env
npm install
npm run dev
```

La app abre en http://localhost:5173.

| Variable | Descripción |
|---|---|
| `VITE_API_URL` | URL base de la API, con `/api/v1` al final |

## Estructura

```
src/
├── components/   componentes reutilizables (PrivateRoute, kit de componentes)
├── contexts/     AuthContext y AuthProvider
├── hooks/        hooks propios (useAuth, useProjects...)
├── pages/        una página por ruta
├── services/     llamadas a la API, una por módulo (authService, projectService...)
├── utils/        utilidades (tokenStorage)
└── routes.jsx    todas las rutas de la app
```

## Cómo llamar a la API

Cada módulo tiene su service en `src/services` y usa el cliente `api`, que ya tiene la URL base, agrega el token y lo renueva solo cuando vence. No hay que poner headers a mano.

```js
// src/services/projectService.js
import api from './api'

export const projectService = {
  getAll: (params) => api.get('/projects', { params }).then((res) => res.data),
  getById: (id) => api.get(`/projects/${id}`).then((res) => res.data),
}
```

## Usuario actual

```js
import { useAuth } from '../hooks/useAuth'

const { user, isAuthenticated, login, register, logout } = useAuth()
// user = { email, rol } con rol CLIENT, FREELANCER o ADMIN
```

## Cómo proteger una ruta

Las rutas se agregan en `src/routes.jsx`, con la página cargada con `lazy`:

```jsx
const MyProjects = lazy(() => import('./pages/MyProjects'))

// Cualquier usuario con sesión
<Route element={<PrivateRoute />}>
  <Route path="/contracts" element={<Contracts />} />
</Route>

// Solo un rol
<Route element={<PrivateRoute roles={['CLIENT']} />}>
  <Route path="/projects/me" element={<MyProjects />} />
</Route>
```

Sin sesión, `PrivateRoute` manda a `/login` y después del login se vuelve a la ruta pedida. Con un rol que no corresponde, manda a `/`.

## Renovación del token

El access token dura 15 minutos. Cuando una petición recibe 401, el interceptor de `services/api.js` pide un token nuevo a `/auth/refresh` y reintenta la petición una sola vez. Si varias peticiones reciben 401 al mismo tiempo, todas esperan la misma renovación. Si el refresh también falla, se cierra la sesión.
