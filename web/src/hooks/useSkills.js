import { skillService } from '../services/skillService'
import { useAsync } from './useAsync'

export function useSkills() {
  return useAsync((options) => skillService.getAll(options), [])
}
