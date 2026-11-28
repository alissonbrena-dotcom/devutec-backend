package pe.edu.utec.devutec.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Pago en dos partes. Las etapas son 10 / 30 / 40 / 10 % y la bolsa de emergencia es un 10 % aparte
// (igual que ContractStagePlan en el contrato). El cliente paga así:
//   Pago 1 (50 %, al aceptar): Descubrimiento 10 % + Diseño 30 % + bolsa de emergencia 10 %.
//   Pago 2 (50 %, al aprobar Diseño): Desarrollo 40 % + Entrega final 10 %.
// La bolsa protege al estudiante: si el cliente desaparece, con ella se le paga lo que ya entregó; si todo va bien,
// se le libera al final. Así el estudiante nunca trabaja una etapa sin pagar.
// Los montos se reparten partiendo los totales, para que los dos pagos sumen exactamente el total, al céntimo.
@Component
public class PaymentPlanner {

    public static final int FIRST_PAYMENT_PERCENT = 50;
    public static final int EMERGENCY_FUND_PERCENT = 10;

    public List<InstallmentDTO> plan(BigDecimal freelancerTotal, BigDecimal clientTotal) {
        BigDecimal freelancer1 = half(freelancerTotal);
        BigDecimal client1 = half(clientTotal);
        BigDecimal freelancer2 = freelancerTotal.setScale(2, RoundingMode.HALF_UP).subtract(freelancer1);
        BigDecimal client2 = clientTotal.setScale(2, RoundingMode.HALF_UP).subtract(client1);
        return List.of(
                new InstallmentDTO(1, FIRST_PAYMENT_PERCENT, freelancer1, client1.subtract(freelancer1), client1,
                        "Al aceptar la propuesta",
                        List.of("Descubrimiento (10 %)", "Diseño (30 %)", "Bolsa de emergencia (10 %)")),
                new InstallmentDTO(2, 100 - FIRST_PAYMENT_PERCENT, freelancer2, client2.subtract(freelancer2), client2,
                        "Al aprobar la etapa de Diseño, antes de empezar el Desarrollo",
                        List.of("Desarrollo (40 %)", "Entrega final (10 %)")));
    }

    private static BigDecimal half(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(FIRST_PAYMENT_PERCENT))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // number: 1 o 2 · freelancerAmount: lo que recibe el estudiante · commission: tarifa DEVUTEC · clientAmount: lo que paga el cliente
    // dueAt: cuándo se paga · covers: qué etapas cubre (incluida la bolsa de emergencia en el pago 1)
    public record InstallmentDTO(int number, int percent, BigDecimal freelancerAmount, BigDecimal commission,
                                 BigDecimal clientAmount, String dueAt, List<String> covers) {
    }
}
