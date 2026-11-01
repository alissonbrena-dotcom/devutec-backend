import { projectService } from '../services/projectService'
import { useAsync } from './useAsync'

export function useProject(id) {
  return useAsync((options) => projectService.getById(id, options), [id])
}
