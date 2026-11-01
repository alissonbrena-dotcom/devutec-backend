import { useAuth } from '../hooks/useAuth'

export default function Home() {
  const { user, logout } = useAuth()

  return (
    <main>
      <h1>DevUTEC</h1>
      <p>
        Sesión iniciada como {user.email} ({user.rol})
      </p>
      <button onClick={logout}>Cerrar sesión</button>
    </main>
  )
}
