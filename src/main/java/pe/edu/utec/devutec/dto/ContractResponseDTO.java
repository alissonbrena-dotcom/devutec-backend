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
    // Parte del monto que no es de ninguna etapa: la bolsa de emergencia (0 en contratos de una sola etapa).
    // Como el monto del contrato es lo que paga el cliente, incluye la comisión de DEVUTEC.
    private BigDecimal emergencyFund;
    // Lo que pagó el cliente por las etapas ya aprobadas (incluye la comisión de DEVUTEC), para mostrar
    // "Liberado" y "En garantía" desde el lado del cliente. No es lo que recibe el estudiante (eso es monto / 1,10).
    private BigDecimal releasedAmount;
    // Pago en dos partes, con lo que se cobró de verdad en Stripe. amount es lo que paga el cliente en total
    // (con la comisión incluida): studentAmount es lo que recibe el estudiante y commissionAmount la tarifa de DEVUTEC.
    // En los contratos anteriores al pago en dos partes la lista viene vacía y los montos en null.
    private List<InstallmentResponseDTO> installments;
    private BigDecimal studentAmount;
    private BigDecimal commissionAmount;
}