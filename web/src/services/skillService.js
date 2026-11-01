import api from './api'

export const skillService = {
  async getAll({ signal } = {}) {
    const { data } = await api.get('/skills', { signal })
    return data
  },
}
