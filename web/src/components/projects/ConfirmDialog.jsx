import { useEffect, useRef } from 'react'

// Ventana de confirmación accesible usando <dialog> nativo
export default function ConfirmDialog({ open, title, message, confirmLabel = 'Confirmar', busy, onConfirm, onCancel }) {
  const ref = useRef(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog ref={ref} className="dialog" onCancel={onCancel}>
      <h2>{title}</h2>
      <p>{message}</p>
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancelar
        </button>
        <button type="button" className="danger" onClick={onConfirm} disabled={busy}>
          {busy ? 'Procesando...' : confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
