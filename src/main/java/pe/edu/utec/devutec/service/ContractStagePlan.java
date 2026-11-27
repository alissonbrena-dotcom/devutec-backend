package pe.edu.utec.devutec.service;

import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.StageStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// Arma las etapas de un contrato y calcula cuánto vale cada una.
// Pago 1 (50 %): Descubrimiento + Diseño (40 %) y 10 % de bolsa de emergencia.
// Pago 2 (50 %): Desarrollo + Entrega final.
public final class ContractStagePlan {

    // Porcentaje del contrato que se guarda como bolsa de emergencia (no es una etapa)
    public static final int EMERGENCY_FUND_PERCENT = 10;

    record StageTemplate(String name, int percentage, int paymentPart) {
    }

    static final List<StageTemplate> DEFAULT_STAGES = List.of(
            new StageTemplate("Descubrimiento", 10, 1),
            new StageTemplate("Diseño", 30, 1),
            new StageTemplate("Desarrollo", 40, 2),
            new StageTemplate("Entrega final", 10, 2)
    );

    // Contratos creados antes de las etapas: una sola etapa con todo el monto
    static final StageTemplate SINGLE_STAGE = new StageTemplate("Trabajo completo", 100, 1);

    private ContractStagePlan() {
    }

    // Crea las etapas de un contrato nuevo. Los montos salen del precio aceptado y las fechas se reparten
    // entre hoy y el plazo acordado según cuánto vale cada etapa.
    public static void addDefaultStages(Contract contract, BigDecimal price, LocalDate deadline) {
        addStages(contract, DEFAULT_STAGES, price, LocalDate.now(), deadline);
        contract.getStages().get(0).setStatus(StageStatus.IN_PROGRESS);
    }

    // Contratos creados antes de las etapas: una sola etapa del 100 %, en el estado que corresponde al contrato
    public static void addSingleStage(Contract contract) {
        addStages(contract, List.of(SINGLE_STAGE), contract.getPayment().getAmount(), null, null);
        ContractStage stage = contract.getStages().get(0);
        stage.setStatus(switch (contract.getStatus()) {
            case IN_PROGRESS -> StageStatus.IN_PROGRESS;
            case DELIVERED -> StageStatus.DELIVERED;
            case CONFIRMED -> StageStatus.APPROVED;
            case CANCELLED -> StageStatus.CANCELLED;
        });
        stage.setDueDate(contract.getApplication().getProject().getDeadline());
        stage.setDeliveryUrl(contract.getDeliveryUrl());
        stage.setDeliveryNote(contract.getDeliveryNote());
        stage.setDeliveredAt(contract.getDeliveredAt());
    }

    static void addStages(Contract contract, List<StageTemplate> templates, BigDecimal price,
                          LocalDate start, LocalDate deadline) {
        int totalPercent = templates.stream().mapToInt(StageTemplate::percentage).sum();
        long days = deadline == null ? 0 : Math.max(1, ChronoUnit.DAYS.between(start, deadline));
        int accumulated = 0;
        for (int i = 0; i < templates.size(); i++) {
            StageTemplate template = templates.get(i);
            accumulated += template.percentage();

            ContractStage stage = new ContractStage();
            stage.setPosition(i + 1);
            stage.setName(template.name());
            stage.setPercentage(template.percentage());
            stage.setAmount(percentOf(price, template.percentage()));
            stage.setPaymentPart(template.paymentPart());
            stage.setStatus(StageStatus.PENDING);
            if (deadline != null) {
                boolean last = i == templates.size() - 1;
                stage.setDueDate(last ? deadline : start.plusDays(Math.round((double) days * accumulated / totalPercent)));
            }
            contract.addStage(stage);
        }
    }

    // La etapa en la que se está trabajando: la primera que no está aprobada ni cancelada
    public static Optional<ContractStage> currentStage(Contract contract) {
        return contract.getStages().stream()
                .filter(stage -> stage.getStatus() != StageStatus.APPROVED && stage.getStatus() != StageStatus.CANCELLED)
                .findFirst();
    }

    public static boolean isLastStage(Contract contract, ContractStage stage) {
        return contract.getStages().get(contract.getStages().size() - 1) == stage;
    }

    // Lo que queda del monto después de sumar las etapas: la bolsa de emergencia (0 si es una sola etapa)
    public static BigDecimal emergencyFund(Contract contract, BigDecimal price) {
        if (contract.getStages().isEmpty() || price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal stagesTotal = contract.getStages().stream()
                .map(ContractStage::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return price.subtract(stagesTotal).max(BigDecimal.ZERO);
    }

    static BigDecimal percentOf(BigDecimal price, int percent) {
        return price.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
