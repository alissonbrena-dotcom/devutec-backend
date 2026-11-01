import api from '../services/api'
import { useAsync } from './useAsync'

// Datos completos del usuario con sesión (incluye su id) desde GET /users/me
export function useCurrentUser() {
  return useAsync(async ({ signal }) => (await api.get('/users/me', { signal })).data, [])
}
