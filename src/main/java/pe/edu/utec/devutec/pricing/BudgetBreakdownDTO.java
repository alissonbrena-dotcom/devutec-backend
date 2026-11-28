package pe.edu.utec.devutec.pricing;

import java.math.BigDecimal;

// clientTotal: lo que paga el cliente · freelancerAmount: lo que recibe el estudiante · commission: tarifa de DEVUTEC
public record BudgetBreakdownDTO(BigDecimal clientTotal, BigDecimal freelancerAmount, BigDecimal commission, BigDecimal rate) {
}
