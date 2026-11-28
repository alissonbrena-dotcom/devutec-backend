package pe.edu.utec.devutec.metrics;

import java.math.BigDecimal;

// Membresías del panel de métricas. Los 3 primeros meses son gratis (empiezan con el primer dato de la web)
// y después el cliente elige Básico o Pro. Se cobran con una suscripción mensual de Stripe.
public enum MetricsPlan {
    NONE(BigDecimal.ZERO, "Sin plan"),
    BASIC(new BigDecimal("19.00"), "Métricas DEVUTEC · Básico"),
    PRO(new BigDecimal("39.00"), "Métricas DEVUTEC · Pro");

    private final BigDecimal monthlyPrice;
    private final String productName;

    MetricsPlan(BigDecimal monthlyPrice, String productName) {
        this.monthlyPrice = monthlyPrice;
        this.productName = productName;
    }

    public BigDecimal monthlyPrice() {
        return monthlyPrice;
    }

    public String productName() {
        return productName;
    }
}
