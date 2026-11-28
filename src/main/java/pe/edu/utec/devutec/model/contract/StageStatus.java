package pe.edu.utec.devutec.model.contract;

// Etapa en la que está cada parte del contrato (Descubrimiento, Diseño, Desarrollo, Entrega final)
public enum StageStatus {
    PENDING,            // Todavía no empieza: espera a que se apruebe la anterior
    IN_PROGRESS,        // El freelancer la está trabajando
    DELIVERED,          // El freelancer la entregó y el cliente tiene que revisarla
    CHANGES_REQUESTED,  // El cliente pidió cambios: el freelancer la vuelve a entregar
    APPROVED,           // El cliente la aprobó
    CANCELLED           // El contrato se canceló antes de terminarla
}
