package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContractResponseDTO {
    private Long id;
    private String projectTitle;
    private String freelancerName;
    private String status;
    private BigDecimal amount;
    private String paymentStatus;
    private LocalDateTime startedAt;
    private LocalDateTime deliveredAt;
    private Long projectId;
    private Long clientId;
    private Long freelancerUserId;
    private String clientName;
    private LocalDate deadline;
    // true cuando el cliente ya pagó el contrato con tarjeta (Stripe, modo prueba)
    private boolean paidWithCard;
    // Celulares de las dos personas para escribirse por WhatsApp. Solo los ve quien participa del contrato
    // (el backend responde 403 a los demás) y no se mandan si el contrato está cancelado. null si no lo agregó.
    private String clientPhone;
    private String freelancerPhone;
    // Link al trabajo entregado y la nota del freelancer. null mientras no entregue.
    private String deliveryUrl;
    private String deliveryNote;
    // Etapas del trabajo, en orden, y la posición de la que se está trabajando (null si ya terminó o se canceló)
    private List<ContractStageResponseDTO> stages;
    private Integer currentStage;
    // Parte del monto que no es de ninguna etapa: la bolsa de emergencia (0 en contratos de una sola etapa).
    // Como el monto del contrato es lo que paga el cliente, incluye la comisión de DEVUTEC.
    private BigDecimal emergencyFund;
    // Lo que pagó el cliente por las etapas ya aprobadas (incluye la comisión de DEVUTEC), para mostrar
    // "Liberado" y "En garantía" desde el lado del cliente. No es lo que recibe el estudiante (eso es monto / 1,10).
    private BigDecimal releasedAmount;
}