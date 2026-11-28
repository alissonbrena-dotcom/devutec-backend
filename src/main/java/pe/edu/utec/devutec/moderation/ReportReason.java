package pe.edu.utec.devutec.moderation;

// Por qué se reporta a alguien
public enum ReportReason {
    PAGO_POR_FUERA,      // Pidió pagar o cobrar fuera de DEVUTEC
    CONTACTO_POR_FUERA,  // Pidió hablar o trabajar fuera de DEVUTEC antes del contrato
    FILTRO_AUTOMATICO,   // Lo detectó el filtro al intentar enviar un texto (no lo reporta una persona)
    OTRO
}
