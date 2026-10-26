import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <main>
      <h1>Página no encontrada</h1>
      <Link to="/">Volver al inicio</Link>
    </main>
  )
}
