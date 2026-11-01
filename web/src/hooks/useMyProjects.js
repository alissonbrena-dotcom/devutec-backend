import { projectService } from '../services/projectService'
import { useAsync } from './useAsync'

// Proyectos publicados por el cliente con sesión iniciada (GET /projects/me)
export function useMyProjects(params) {
  const key = JSON.stringify(params)
  return useAsync((options) => projectService.getMine(params, options), [key])
}
