import { projectService } from '../services/projectService'
import { useAsync } from './useAsync'

// Lista de proyectos con filtros y paginación (page empieza en 1)
export function useProjects(params) {
  const key = JSON.stringify(params)
  return useAsync((options) => projectService.getAll(params, options), [key])
}
