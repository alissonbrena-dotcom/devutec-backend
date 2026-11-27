package pe.edu.utec.devutec.guide;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Forma fija de la respuesta de la IA para la Mini guía (salidas estructuradas).
// Las horas de la IA solo reparten el trabajo entre tareas: el total se ajusta a la tabla de horas del backend.
public record StageGuideAiReply(
        @JsonPropertyDescription("Tareas concretas de ESTA etapa, en el orden en que conviene hacerlas, entre 3 y 7")
        List<AiTask> tasks,

        @JsonPropertyDescription("Entre 2 y 4 preguntas cortas que el estudiante debería hacerle al cliente en esta etapa, escritas como se las diría (tuteando)")
        List<String> clientQuestions,

        @JsonPropertyDescription("Entre 1 y 3 riesgos de esta etapa que un estudiante sin experiencia suele olvidar, cada uno en una oración")
        List<String> risks,

        @JsonPropertyDescription("Un consejo corto para esta etapa, de una oración")
        String tip
) {
    public record AiTask(
            @JsonPropertyDescription("La tarea, empezando con un verbo, en una línea")
            String title,
            @JsonPropertyDescription("Horas estimadas para la tarea, entre 1 y 20")
            int hours
    ) {
    }
}
