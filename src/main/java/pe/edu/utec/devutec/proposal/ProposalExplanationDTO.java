package pe.edu.utec.devutec.proposal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Lo que ve el cliente al pedir que la IA le explique una propuesta.
// summary y las diferencias los calcula el backend; reason, forYou y questions son de la IA
// (vacíos si la propuesta no cambia nada, porque ahí no se llama a la IA).
public record ProposalExplanationDTO(
        Long applicationId,
        String studentName,
        boolean changed,
        String summary,
        BigDecimal budget,
        BigDecimal proposedPrice,
        BigDecimal priceDifference,
        LocalDate deadline,
        LocalDate proposedDeadline,
        long daysDifference,
        String reason,
        List<String> forYou,
        List<String> questions
) {
}
