import { STATUS_LABELS } from '../../utils/format'

export default function ProjectStatusBadge({ status }) {
  return (
    <span className={`badge badge-${status?.toLowerCase()}`}>{STATUS_LABELS[status] ?? status}</span>
  )
}
