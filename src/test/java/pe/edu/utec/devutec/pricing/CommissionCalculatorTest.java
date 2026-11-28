package pe.edu.utec.devutec.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionCalculatorTest {

    private final CommissionCalculator calculator = new CommissionCalculator(new BigDecimal("0.10"));

    @Test
    void fromClientTotal_elPresupuestoDelClienteYaIncluyeLaComision() {
        BudgetBreakdownDTO breakdown = calculator.fromClientTotal(new BigDecimal("880"));

        assertEquals(new BigDecimal("880.00"), breakdown.clientTotal());
        assertEquals(new BigDecimal("800.00"), breakdown.freelancerAmount());
        assertEquals(new BigDecimal("80.00"), breakdown.commission());
    }

    @Test
    void fromFreelancerAmount_sumaLaComisionAlPrecioDelEstudiante() {
        BudgetBreakdownDTO breakdown = calculator.fromFreelancerAmount(new BigDecimal("750"));

        assertEquals(new BigDecimal("825.00"), breakdown.clientTotal());
        assertEquals(new BigDecimal("750.00"), breakdown.freelancerAmount());
        assertEquals(new BigDecimal("75.00"), breakdown.commission());
    }

    @Test
    void fromClientTotal_redondeaAlCentimoYCuadraElTotal() {
        BudgetBreakdownDTO breakdown = calculator.fromClientTotal(new BigDecimal("1000"));

        assertEquals(new BigDecimal("909.09"), breakdown.freelancerAmount());
        assertEquals(breakdown.clientTotal(), breakdown.freelancerAmount().add(breakdown.commission()));
    }
}
