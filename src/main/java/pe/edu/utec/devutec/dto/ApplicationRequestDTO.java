package pe.edu.utec.devutec.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ApplicationRequestDTO {
    private Long projectID;
    private String message;
    private BigDecimal proposedPrice;

    public ApplicationRequestDTO(){

    }

    public ApplicationRequestDTO (Long projectID, String message, BigDecimal proposedPrice) {
        this.projectID = projectID;
        this.message = message;
        this.proposedPrice = proposedPrice;
    }
}
