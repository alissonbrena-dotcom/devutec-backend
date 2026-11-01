import { useState } from 'react'
import { projectService } from '../services/projectService'
import { errorMessage } from '../utils/errorMessage'

// Crear, editar y eliminar, con estado de "guardando" y mensaje de error
export function useProjectActions() {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const run = async (action) => {
    setSaving(true)
    setError(null)
    try {
      return await action()
    } catch (err) {
      setError(errorMessage(err))
      throw err
    } finally {
      setSaving(false)
    }
  }

  return {
    saving,
    error,
    clearError: () => setError(null),
    create: (project) => run(() => projectService.create(project)),
    update: (id, project) => run(() => projectService.update(id, project)),
    remove: (id) => run(() => projectService.remove(id)),
  }
}
