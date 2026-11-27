package pe.edu.utec.devutec.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentPlannerTest {

    private final PaymentPlanner planner = new PaymentPlanner();

    @Test
    void dosPagosDe50PorCientoConLaBolsaEnElPrimero() {
        List<PaymentPlanner.InstallmentDTO> plan = planner.plan(new BigDecimal("800.00"), new BigDecimal("880.00"));

        assertEquals(2, plan.size());
        assertEquals(new BigDecimal("400.00"), plan.get(0).freelancerAmount());
        assertEquals(new BigDecimal("440.00"), plan.get(0).clientAmount());
        assertEquals(new BigDecimal("40.00"), plan.get(0).commission());
        assertTrue(plan.get(0).covers().stream().anyMatch(item -> item.contains("Bolsa de emergencia")));
        assertEquals(new BigDecimal("400.00"), plan.get(1).freelancerAmount());
        assertEquals(new BigDecimal("440.00"), plan.get(1).clientAmount());
    }

    @Test
    void losDosPagosSumanElTotalAlCentimo() {
        List<PaymentPlanner.InstallmentDTO> plan = planner.plan(new BigDecimal("909.09"), new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("909.09"), plan.get(0).freelancerAmount().add(plan.get(1).freelancerAmount()));
        assertEquals(new BigDecimal("1000.00"), plan.get(0).clientAmount().add(plan.get(1).clientAmount()));
        BigDecimal commissions = plan.get(0).commission().add(plan.get(1).commission());
        assertEquals(new BigDecimal("90.91"), commissions);
    }
}
