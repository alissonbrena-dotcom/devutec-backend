import { Link } from 'react-router-dom'
import ProjectStatusBadge from './ProjectStatusBadge'
import { formatBudget, formatDate } from '../../utils/format'

export default function ProjectCard({ project }) {
  return (
    <article className="card project-card">
      <header className="project-card-header">
        <h3>
          <Link to={`/projects/${project.id}`}>{project.title}</Link>
        </h3>
        <ProjectStatusBadge status={project.status} />
      </header>
      {project.description && <p className="project-card-description">{project.description}</p>}
      <dl className="project-meta">
        <div>
          <dt>Presupuesto</dt>
          <dd>{formatBudget(project.budget)}</dd>
        </div>
        <div>
          <dt>Fecha límite</dt>
          <dd>{formatDate(project.deadline)}</dd>
        </div>
      </dl>
      {project.skills?.length > 0 && (
        <ul className="skill-list">
          {project.skills.map((skill) => (
            <li key={skill.id}>{skill.name}</li>
          ))}
        </ul>
      )}
    </article>
  )
}
