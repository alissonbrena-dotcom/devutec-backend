import { useCallback, useEffect, useState } from 'react'
import { AuthContext } from './authContext'
import { authService } from '../services/authService'
import { setSessionExpiredHandler } from '../services/api'
import { tokenStorage } from '../utils/tokenStorage'

export function AuthProvider({ children }) {
  // Se recupera la sesión guardada para no perderla al recargar la página
  const [user, setUser] = useState(() => tokenStorage.getUser())

  const logout = useCallback(() => {
    tokenStorage.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(logout)
  }, [logout])

  // Mientras no exista GET /users/me, el usuario se arma con la respuesta del login
  const startSession = (data) => {
    const sessionUser = { email: data.email, rol: data.rol }
    tokenStorage.setTokens(data.accessToken, data.refreshToken)
    tokenStorage.setUser(sessionUser)
    setUser(sessionUser)
    return sessionUser
  }

  const login = async (credentials) => startSession(await authService.login(credentials))

  // El registro también devuelve tokens, así que el usuario queda logueado
  const register = async (data) => startSession(await authService.register(data))

  const value = {
    user,
    isAuthenticated: user !== null,
    login,
    register,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
