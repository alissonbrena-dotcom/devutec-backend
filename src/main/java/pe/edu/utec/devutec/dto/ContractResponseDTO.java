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
    // Ya no se usan: DEVUTEC no comparte celulares entre cliente y estudiante, todo se coordina en la plataforma.
    // Siempre llegan null; quedan para no romper versiones anteriores de la web y la app.
    private String clientPhone;
    private String freelancerPhone;
    // Link al trabajo entregado y la nota del freelancer. null mientras no entregue.
    private String deliveryUrl;
    private String deliveryNote;
    // Etapas del trabajo, en orden, y la posición de la que se está trabajando (null si ya terminó o se canceló)
    private List<ContractStageResponseDTO> stages;
    private Integer currentStage;
    // Parte del monto que no es de ninguna etapa: la bolsa de emergencia (0 en contratos de una sola etapa)
    private BigDecimal emergencyFund;
    // Suma de las etapas ya aprobadas por el cliente, para mostrar "Liberado" y "En garantía"
    private BigDecimal releasedAmount;
}