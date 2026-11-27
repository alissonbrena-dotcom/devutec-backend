package pe.edu.utec.devutec.assistant;

import pe.edu.utec.devutec.pricing.BudgetBreakdownDTO;
import pe.edu.utec.devutec.pricing.FeatureQuantityDTO;
import pe.edu.utec.devutec.pricing.PriceEstimateDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// reply: lo que dice el asistente · outOfScope: el mensaje no era sobre el proyecto · ready: ya hay borrador
// remaining: mensajes que le quedan hoy · draft: el proyecto armado (solo cuando ready es true)
public record ProjectAssistantResponseDTO(String reply, boolean outOfScope, boolean ready, int remaining, Draft draft) {

    // Borrador para llenar el formulario de "Nuevo proyecto".
    // skillIds y skillNames NO se le muestran al cliente: se envían ocultos al crear el proyecto y los ve el estudiante.
    // budget: desglose del presupuesto del cliente (null si no dijo cuánto) · commissionRate: para recalcular en pantalla
    public record Draft(
            String title,
            String description,
            String category,
            Set<Long> skillIds,
            List<String> skillNames,
            List<FeatureQuantityDTO> features,
            LocalDate deadline,
            String brandStyle,
            PriceEstimateDTO estimate,
            BudgetBreakdownDTO budget,
            BigDecimal commissionRate,
            List<StageAmount> stages
    ) {
    }

    // Plan de pagos por etapas propuesto al cliente: lo que recibe el estudiante y lo que paga el cliente por etapa
    public record StageAmount(String name, int percent, BigDecimal freelancerAmount, BigDecimal clientAmount,
                              int hoursMin, int hoursMax) {
    }
}
