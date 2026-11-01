import { useState } from 'react'
import { useSkills } from '../../hooks/useSkills'

const EMPTY = { title: '', description: '', budget: '', deadline: '', skillIds: [] }

function toFormValues(project) {
  if (!project) return EMPTY
  return {
    title: project.title ?? '',
    description: project.description ?? '',
    budget: project.budget ?? '',
    deadline: project.deadline ?? '',
    skillIds: project.skills?.map((skill) => skill.id) ?? [],
  }
}

function validate(values) {
  const errors = {}
  if (!values.title.trim()) errors.title = 'El título es obligatorio'
  else if (values.title.length > 255) errors.title = 'Máximo 255 caracteres'
  if (values.budget === '') errors.budget = 'El presupuesto es obligatorio'
  else if (Number(values.budget) <= 0) errors.budget = 'El presupuesto debe ser mayor a cero'
  return errors
}

// Formulario compartido por "Publicar proyecto" y "Editar proyecto"
export default function ProjectForm({ initialProject, submitLabel, saving, serverError, onSubmit }) {
  const [values, setValues] = useState(() => toFormValues(initialProject))
  const [errors, setErrors] = useState({})
  const skills = useSkills()

  const setField = (name, value) => {
    setValues((prev) => ({ ...prev, [name]: value }))
    setErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  const toggleSkill = (id) => {
    setValues((prev) => ({
      ...prev,
      skillIds: prev.skillIds.includes(id) ? prev.skillIds.filter((s) => s !== id) : [...prev.skillIds, id],
    }))
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    onSubmit({
      title: values.title.trim(),
      description: values.description.trim() || null,
      budget: Number(values.budget),
      deadline: values.deadline || null,
      skillIds: values.skillIds,
    })
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate>
      <label>
        Título *
        <input
          value={values.title}
          onChange={(event) => setField('title', event.target.value)}
          aria-invalid={Boolean(errors.title)}
          maxLength={255}
        />
        {errors.title && <span className="field-error">{errors.title}</span>}
      </label>

      <label>
        Descripción
        <textarea
          rows={5}
          value={values.description}
          onChange={(event) => setField('description', event.target.value)}
        />
      </label>

      <div className="form-row">
        <label>
          Presupuesto (S/) *
          <input
            type="number"
            min="1"
            step="0.01"
            value={values.budget}
            onChange={(event) => setField('budget', event.target.value)}
            aria-invalid={Boolean(errors.budget)}
          />
          {errors.budget && <span className="field-error">{errors.budget}</span>}
        </label>

        <label>
          Fecha límite
          <input type="date" value={values.deadline} onChange={(event) => setField('deadline', event.target.value)} />
        </label>
      </div>

      <fieldset>
        <legend>Habilidades requeridas</legend>
        {skills.loading && <p>Cargando habilidades...</p>}
        {skills.error && <p className="error">{skills.error}</p>}
        <div className="skill-options">
          {skills.data?.map((skill) => (
            <label key={skill.id} className="checkbox">
              <input
                type="checkbox"
                checked={values.skillIds.includes(skill.id)}
                onChange={() => toggleSkill(skill.id)}
              />
              {skill.name}
            </label>
          ))}
        </div>
      </fieldset>

      {serverError && <p className="error">{serverError}</p>}

      <button type="submit" className="primary" disabled={saving}>
        {saving ? 'Guardando...' : submitLabel}
      </button>
    </form>
  )
}
