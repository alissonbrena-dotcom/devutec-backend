import { Link, useNavigate } from 'react-router-dom'
import ProjectForm from '../../components/projects/ProjectForm'
import { useProjectActions } from '../../hooks/useProjectActions'
import '../../components/projects/projects.css'

export default function ProjectCreate() {
  const navigate = useNavigate()
  const actions = useProjectActions()

  const handleSubmit = async (project) => {
    try {
      const created = await actions.create(project)
      navigate(`/projects/${created.id}`, { replace: true })
    } catch {
      // El mensaje ya queda en actions.error
    }
  }

  return (
    <main className="container narrow">
      <Link to="/my-projects" className="back-link">
        ← Mis proyectos
      </Link>
      <h1>Publicar proyecto</h1>
      <ProjectForm
        submitLabel="Publicar"
        saving={actions.saving}
        serverError={actions.error}
        onSubmit={handleSubmit}
      />
    </main>
  )
}
