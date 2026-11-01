import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import ProjectStatusBadge from '../../components/projects/ProjectStatusBadge'
import ConfirmDialog from '../../components/projects/ConfirmDialog'
import { useProject } from '../../hooks/useProject'
import { useProjectActions } from '../../hooks/useProjectActions'
import { useCurrentUser } from '../../hooks/useCurrentUser'
import { useAuth } from '../../hooks/useAuth'
import { formatBudget, formatDate } from '../../utils/format'
import '../../components/projects/projects.css'

export default function ProjectDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: project, loading, error, reload } = useProject(id)
  const { data: me } = useCurrentUser()
  const actions = useProjectActions()
  const [confirmOpen, setConfirmOpen] = useState(false)

  const isOwner = Boolean(me && project && me.id === project.clientId)
  const canManage = isOwner || user?.rol === 'ADMIN'

  const handleDelete = async () => {
    try {
      await actions.remove(id)
      navigate(user?.rol === 'CLIENT' ? '/my-projects' : '/projects', {
        replace: true,
        state: { message: 'Proyecto eliminado' },
      })
    } catch {
      setConfirmOpen(false)
    }
  }

  if (loading) return <main className="container state">Cargando proyecto...</main>

  if (error) {
    return (
      <main className="container state error">
        <p>{error}</p>
        <button type="button" onClick={reload}>
          Reintentar
        </button>{' '}
        <Link to="/projects">Volver a proyectos</Link>
      </main>
    )
  }

  return (
    <main className="container">
      <Link to="/projects" className="back-link">
        ← Volver a proyectos
      </Link>

      <article className="card project-detail">
        <header className="project-card-header">
          <h1>{project.title}</h1>
          <ProjectStatusBadge status={project.status} />
        </header>

        <dl className="project-meta">
          <div>
            <dt>Presupuesto</dt>
            <dd>{formatBudget(project.budget)}</dd>
          </div>
          <div>
            <dt>Fecha límite</dt>
            <dd>{formatDate(project.deadline)}</dd>
          </div>
          <div>
            <dt>Publicado</dt>
            <dd>{formatDate(project.createdAt)}</dd>
          </div>
        </dl>

        <section>
          <h2>Descripción</h2>
          <p className="pre-line">{project.description || 'Sin descripción.'}</p>
        </section>

        {project.skills?.length > 0 && (
          <section>
            <h2>Habilidades requeridas</h2>
            <ul className="skill-list">
              {project.skills.map((skill) => (
                <li key={skill.id}>{skill.name}</li>
              ))}
            </ul>
          </section>
        )}

        {actions.error && <p className="error">{actions.error}</p>}

        <footer className="actions">
          {user?.rol === 'FREELANCER' && project.status === 'OPEN' && (
            <Link className="button primary" to={`/projects/${project.id}/apply`}>
              Postular
            </Link>
          )}
          {isOwner && (
            <Link className="button" to={`/projects/${project.id}/applications`}>
              Ver postulaciones
            </Link>
          )}
          {canManage && (
            <>
              <Link className="button" to={`/projects/${project.id}/edit`}>
                Editar
              </Link>
              <button type="button" className="danger" onClick={() => setConfirmOpen(true)}>
                Eliminar
              </button>
            </>
          )}
        </footer>
      </article>

      <ConfirmDialog
        open={confirmOpen}
        title="¿Eliminar proyecto?"
        message={`Se eliminará "${project.title}". Esta acción no se puede deshacer.`}
        confirmLabel="Sí, eliminar"
        busy={actions.saving}
        onConfirm={handleDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </main>
  )
}
