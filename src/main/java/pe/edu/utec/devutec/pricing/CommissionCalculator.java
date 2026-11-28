package pe.edu.utec.devutec.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Comisión de DEVUTEC: la paga el cliente y el estudiante recibe el 100 % de su precio.
// El presupuesto que escribe el cliente YA INCLUYE la comisión: con S/ 880 y 10 %, el estudiante recibe S/ 800
// y DEVUTEC S/ 80. La tasa se cambia con COMMISSION_RATE.
@Component
public class CommissionCalculator {

    private final BigDecimal rate;

    public CommissionCalculator(@Value("${app.commission-rate:0.10}") BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getRate() {
        return rate;
    }

    // Desde el total que paga el cliente: cuánto recibe el estudiante y cuánto es la comisión
    public BudgetBreakdownDTO fromClientTotal(BigDecimal clientTotal) {
        BigDecimal freelancer = clientTotal.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
        return new BudgetBreakdownDTO(money(clientTotal), freelancer, money(clientTotal.subtract(freelancer)), rate);
    }

    // Desde lo que cobra el estudiante: cuánto paga el cliente en total
    public BudgetBreakdownDTO fromFreelancerAmount(BigDecimal freelancerAmount) {
        BigDecimal commission = money(freelancerAmount.multiply(rate));
        return new BudgetBreakdownDTO(money(freelancerAmount.add(commission)), money(freelancerAmount), commission, rate);
    }

    public BigDecimal clientTotalOf(BigDecimal freelancerAmount) {
        return money(freelancerAmount.multiply(BigDecimal.ONE.add(rate)));
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
