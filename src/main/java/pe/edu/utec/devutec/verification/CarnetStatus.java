package pe.edu.utec.devutec.verification;

// Segunda verificación del estudiante: la foto de su carnet universitario, revisada por el admin
public enum CarnetStatus {
    PENDING,   // Lo subió y espera la revisión
    APPROVED,  // El admin lo aprobó: insignia «Carnet verificado»
    REJECTED   // El admin lo rechazó (foto borrosa, otro documento…): puede subir otra
}
