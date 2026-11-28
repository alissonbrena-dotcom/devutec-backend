package pe.edu.utec.devutec.quality;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Forma fija de la respuesta de la IA cuando la web no pasa el estándar (salidas estructuradas)
public record QualityAiReply(
        @JsonPropertyDescription("Entre 2 y 6 tareas concretas para que el estudiante arregle lo que falla, ordenadas por impacto, cada una empezando con un verbo")
        List<String> tasks,

        @JsonPropertyDescription("Una o dos oraciones para el cliente, sin palabras técnicas, sobre el estado de su web")
        String forClient
) {
}
