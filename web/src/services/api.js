import axios from 'axios'
import { tokenStorage } from '../utils/tokenStorage'

export const API_URL = import.meta.env.VITE_API_URL

const api = axios.create({
  baseURL: API_URL,
  timeout: 15000,
})

// Agrega el access token a todas las peticiones
api.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// AuthProvider registra aquí qué hacer cuando la sesión ya no se puede renovar
let onSessionExpired = () => {}
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler
}

// Renovación en curso. Si varias peticiones reciben 401 a la vez,
// todas esperan esta misma promesa en lugar de pedir un refresh cada una.
let refreshPromise = null

async function refreshTokens() {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) {
    throw new Error('No hay refresh token')
  }
  // Se usa axios directamente para no pasar por los interceptores de api
  const { data } = await axios.post(`${API_URL}/auth/refresh`, { refreshToken })
  tokenStorage.setTokens(data.accessToken, data.refreshToken)
  return data.accessToken
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const isAuthRequest = original?.url?.startsWith('/auth/')

    // Solo se renueva ante un 401, una vez por petición y nunca en login o registro
    if (error.response?.status !== 401 || !original || original._retry || isAuthRequest) {
      return Promise.reject(error)
    }
    original._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = refreshTokens().finally(() => {
          refreshPromise = null
        })
      }
      const accessToken = await refreshPromise
      original.headers.Authorization = `Bearer ${accessToken}`
      return api(original)
    } catch {
      tokenStorage.clear()
      onSessionExpired()
      return Promise.reject(error)
    }
  },
)

export default api
