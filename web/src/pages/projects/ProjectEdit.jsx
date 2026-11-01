import { Link, useNavigate, useParams } from 'react-router-dom'
import ProjectForm from '../../components/projects/ProjectForm'
import { useProject } from '../../hooks/useProject'
import { useProjectActions } from '../../hooks/useProjectActions'
import '../../components/projects/projects.css'

export default function ProjectEdit() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { data: project, loading, error } = useProject(id)
  const actions = useProjectActions()

  const handleSubmit = async (changes) => {
    try {
      await actions.update(id, changes)
      navigate(`/projects/${id}`, { replace: true })
    } catch {
      // El mensaje ya queda en actions.error (por ejemplo 403 si no es el dueño)
    }
  }

  if (loading) return <main className="container state">Cargando proyecto...</main>
  if (error) {
    return (
      <main className="container state error">
        <p>{error}</p>
        <Link to="/projects">Volver a proyectos</Link>
      </main>
    )
  }

  return (
    <main className="container narrow">
      <Link to={`/projects/${id}`} className="back-link">
        ← Volver al proyecto
      </Link>
      <h1>Editar proyecto</h1>
      <ProjectForm
        key={project.id}
        initialProject={project}
        submitLabel="Guardar cambios"
        saving={actions.saving}
        serverError={actions.error}
        onSubmit={handleSubmit}
      />
    </main>
  )
}
