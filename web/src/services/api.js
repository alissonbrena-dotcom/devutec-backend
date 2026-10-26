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

export default api
