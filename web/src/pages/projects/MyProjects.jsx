import { Link, useLocation, useSearchParams } from 'react-router-dom'
import ProjectCard from '../../components/projects/ProjectCard'
import Pagination from '../../components/projects/Pagination'
import { useMyProjects } from '../../hooks/useMyProjects'
import '../../components/projects/projects.css'

export default function MyProjects() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Math.max(Number(searchParams.get('page')) || 1, 1)
  const { data, loading, error, reload } = useMyProjects({ page, size: 9 })

  const handlePageChange = (nextPage) => setSearchParams(nextPage > 1 ? { page: nextPage } : {})

  return (
    <main className="container">
      <header className="page-header">
        <h1>Mis proyectos</h1>
        <Link className="button primary" to="/projects/new">
          + Publicar proyecto
        </Link>
      </header>

      {location.state?.message && <p className="success">{location.state.message}</p>}

      {loading && <p className="state">Cargando tus proyectos...</p>}

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
          <p>Todavía no has publicado proyectos.</p>
          <Link className="button primary" to="/projects/new">
            Publicar mi primer proyecto
          </Link>
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
