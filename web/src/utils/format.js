const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

export function formatBudget(value) {
  return value === null || value === undefined ? '—' : money.format(value)
}

// "2026-11-10" se lee como fecha local para que no se corra un día por la zona horaria
export function formatDate(value) {
  if (!value) return 'Sin fecha límite'
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('es-PE', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

export const STATUS_LABELS = {
  OPEN: 'Abierto',
  IN_PROGRESS: 'En progreso',
  DONE: 'Terminado',
}
