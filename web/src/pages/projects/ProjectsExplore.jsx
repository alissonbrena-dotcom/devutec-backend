import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import ProjectCard from '../../components/projects/ProjectCard'
import ProjectFilters from '../../components/projects/ProjectFilters'
import Pagination from '../../components/projects/Pagination'
import { useProjects } from '../../hooks/useProjects'
import { useSkills } from '../../hooks/useSkills'
import { useDebounce } from '../../hooks/useDebounce'
import { useAuth } from '../../hooks/useAuth'
import '../../components/projects/projects.css'

const FILTER_KEYS = ['status', 'skillId', 'minBudget', 'maxBudget', 'deadlineBefore']
const PAGE_SIZE = 9

// Explorar proyectos: los filtros y la página viven en la URL (?search=&status=&page=2),
// así se puede compartir el enlace o volver atrás sin perder la búsqueda.
export default function ProjectsExplore() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(Number(searchParams.get('page')) || 1, 1)
  const urlSearch = searchParams.get('search') ?? ''

  const filters = useMemo(
    () => Object.fromEntries(FILTER_KEYS.map((key) => [key, searchParams.get(key) ?? ''])),
    [searchParams],
  )

  // El texto se escribe en un estado local y pasa a la URL 400 ms después de dejar de escribir
  const [search, setSearch] = useState(urlSearch)
  const debouncedSearch = useDebounce(search, 400)

  // Si la URL cambia desde fuera (botón atrás, limpiar filtros), el input se sincroniza
  const [prevUrlSearch, setPrevUrlSearch] = useState(urlSearch)
  if (urlSearch !== prevUrlSearch) {
    setPrevUrlSearch(urlSearch)
    setSearch(urlSearch)
  }

  const updateParams = (changes) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      Object.entries(changes).forEach(([key, value]) => {
        if (value === '' || value === null || value === undefined) next.delete(key)
        else next.set(key, value)
      })
      return next
    })
  }

  useEffect(() => {
    if (debouncedSearch !== urlSearch) {
      updateParams({ search: debouncedSearch, page: null })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const { data, loading, error, reload } = useProjects({
    ...filters,
    search: urlSearch,
    page,
    size: PAGE_SIZE,
  })
  const skills = useSkills()

  const handleFilterChange = (key, value) => updateParams({ [key]: value, page: null })
  const handlePageChange = (nextPage) => {
    updateParams({ page: nextPage > 1 ? nextPage : null })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const handleClear = () => {
    setSearch('')
    setSearchParams({})
  }

  return (
    <main className="container">
      <header className="page-header">
        <div>
          <h1>Explorar proyectos</h1>
          {data && <p className="muted">{data.totalElements} proyecto(s) encontrados</p>}
        </div>
        {user?.rol === 'CLIENT' && (
          <Link className="button primary" to="/projects/new">
            + Publicar proyecto
          </Link>
        )}
      </header>

      <ProjectFilters
        search={search}
        onSearchChange={setSearch}
        filters={filters}
        onFilterChange={handleFilterChange}
        skills={skills.data}
        onClear={handleClear}
      />

      {loading && <p className="state">Cargando proyectos...</p>}

      {!loading && error && (
        <div className="state error">
          <p>{error}</p>
          <button type="button" onClick={reload}>
            Reintentar
          </button>
        </div>
      )}

      {!loading && !error && data?.content.length === 0 && (
        <div className="state">
          <p>No hay proyectos que coincidan con tu búsqueda.</p>
          <button type="button" onClick={handleClear}>
            Limpiar filtros
          </button>
        </div>
      )}

      {!loading && !error && data?.content.length > 0 && (
        <>
          <section className="project-grid">
            {data.content.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </section>
          <Pagination page={page} totalPages={data.totalPages} onChange={handlePageChange} />
        </>
      )}
    </main>
  )
}
