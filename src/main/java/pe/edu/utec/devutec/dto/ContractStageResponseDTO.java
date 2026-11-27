package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Una etapa del contrato tal como la ven la web y la app
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContractStageResponseDTO {
    private Long id;
    private Integer position;
    private String name;
    private Integer percentage;
    private BigDecimal amount;
    private Integer paymentPart;
    private String status;
    private LocalDate dueDate;
    private String deliveryUrl;
    private String deliveryNote;
    private LocalDateTime deliveredAt;
    private Integer changeRounds;
    private Integer maxChangeRounds;
    private String changeRequestNote;
    private LocalDateTime approvedAt;
}
