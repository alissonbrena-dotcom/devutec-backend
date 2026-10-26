import api from './api'

// Endpoints de /api/v1/auth. Todos devuelven { accessToken, refreshToken, email, rol }.
export const authService = {
  login: (credentials) => api.post('/auth/login', credentials).then((res) => res.data),

  // data: { nombre, email, password, rol } con rol CLIENT o FREELANCER
  register: (data) => api.post('/auth/register', data).then((res) => res.data),
}
