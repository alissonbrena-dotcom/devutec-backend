import { STATUS_LABELS } from '../../utils/format'

// Controles de filtro. El texto de búsqueda se maneja aparte para aplicarle debounce.
export default function ProjectFilters({ search, onSearchChange, filters, onFilterChange, skills, onClear }) {
  return (
    <form className="filters card" onSubmit={(event) => event.preventDefault()} role="search">
      <label className="filters-search">
        Buscar
        <input
          type="search"
          placeholder="Título o descripción"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </label>

      <label>
        Estado
        <select value={filters.status} onChange={(event) => onFilterChange('status', event.target.value)}>
          <option value="">Todos</option>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>

      <label>
        Habilidad
        <select value={filters.skillId} onChange={(event) => onFilterChange('skillId', event.target.value)}>
          <option value="">Todas</option>
          {skills?.map((skill) => (
            <option key={skill.id} value={skill.id}>
              {skill.name}
            </option>
          ))}
        </select>
      </label>

      <label>
        Presupuesto mín.
        <input
          type="number"
          min="0"
          value={filters.minBudget}
          onChange={(event) => onFilterChange('minBudget', event.target.value)}
        />
      </label>

      <label>
        Presupuesto máx.
        <input
          type="number"
          min="0"
          value={filters.maxBudget}
          onChange={(event) => onFilterChange('maxBudget', event.target.value)}
        />
      </label>

      <label>
        Entrega antes de
        <input
          type="date"
          value={filters.deadlineBefore}
          onChange={(event) => onFilterChange('deadlineBefore', event.target.value)}
        />
      </label>

      <button type="button" onClick={onClear}>
        Limpiar filtros
      </button>
    </form>
  )
}
