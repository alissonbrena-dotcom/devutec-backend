import axios from 'axios'

// Convierte un error de axios en un texto entendible para mostrar en pantalla
export function errorMessage(error, fallback = 'Ocurrió un error. Inténtalo otra vez.') {
  if (axios.isCancel(error)) return null
  const status = error?.response?.status
  const apiMessage = error?.response?.data?.message
  if (status === 403) return 'No tienes permiso para hacer esto.'
  if (status === 404) return 'No encontramos lo que buscas.'
  if (apiMessage) return apiMessage
  if (!error?.response) return 'No hay conexión con el servidor.'
  return fallback
}
