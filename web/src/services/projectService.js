import api from './api'

// Quita filtros vacíos para no mandar ?search=&status= a la API
function cleanParams(params) {
  return Object.fromEntries(
    Object.entries(params)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
      .filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

// En la URL y en la pantalla las páginas empiezan en 1; la API (Spring) empieza en 0.
// La conversión se hace solo aquí para que el resto del código piense en "página 1, 2, 3".
function toApiPage({ page = 1, ...rest }) {
  return cleanParams({ ...rest, page: Math.max(Number(page) || 1, 1) - 1 })
}

export const projectService = {
  async getAll(params = {}, { signal } = {}) {
    const { data } = await api.get('/projects', { params: toApiPage(params), signal })
    return { ...data, page: data.page + 1 }
  },

  async getById(id, { signal } = {}) {
    const { data } = await api.get(`/projects/${id}`, { signal })
    return data
  },

  async getMine(params = {}, { signal } = {}) {
    const { data } = await api.get('/projects/me', { params: toApiPage(params), signal })
    return { ...data, page: data.page + 1 }
  },

  async create(project) {
    const { data } = await api.post('/projects', project)
    return data
  },

  async update(id, project) {
    const { data } = await api.put(`/projects/${id}`, project)
    return data
  },

  async remove(id) {
    await api.delete(`/projects/${id}`)
  },
}
