// page empieza en 1
export default function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null
  return (
    <nav className="pagination" aria-label="Paginación">
      <button type="button" onClick={() => onChange(page - 1)} disabled={page <= 1}>
        ← Anterior
      </button>
      <span>
        Página {page} de {totalPages}
      </span>
      <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages}>
        Siguiente →
      </button>
    </nav>
  )
}
