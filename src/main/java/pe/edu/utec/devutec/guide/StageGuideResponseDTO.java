package pe.edu.utec.devutec.guide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Mini guía de una etapa tal como la ven la web y la app
public record StageGuideResponseDTO(
        Long stageId,
        String stageName,
        List<Task> tasks,
        List<String> clientQuestions,
        List<String> risks,
        String tip,
        Progress progress,
        PriceCheck priceCheck
) {
    public record Task(Long id, String title, int hours, boolean done) {
    }

    // Avance vs. horas: «Diseño: 60 % · vas a tiempo».
    // schedule: ON_TIME, BEHIND o NO_DATE (la etapa no tiene fecha). expectedPercent: cuánto debería llevar a hoy.
    public record Progress(int hoursDone, int hoursTotal, int percent, Integer expectedPercent,
                           String schedule, LocalDate dueDate, String label) {
    }

    // Revisión del precio: cuánto está cobrando por hora comparado con el promedio de los estudiantes.
    // low = true si cobra bastante menos que el promedio. null si el proyecto no tiene horas de referencia.
    public record PriceCheck(BigDecimal amount, int hoursMin, int hoursMax, BigDecimal hourlyRate,
                             BigDecimal averageHourlyRate, boolean low, String message) {
    }
}
