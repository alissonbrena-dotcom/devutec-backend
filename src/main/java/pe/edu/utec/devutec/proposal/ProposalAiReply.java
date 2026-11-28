package pe.edu.utec.devutec.proposal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Forma fija de la respuesta de la IA al explicar una contraoferta (salidas estructuradas).
// Los montos y los días no los calcula la IA: vienen del backend.
public record ProposalAiReply(
        @JsonPropertyDescription("En 1 o 2 oraciones, por qué el estudiante propone este cambio, solo según su mensaje. Si el mensaje no lo explica, dilo")
        String reason,

        @JsonPropertyDescription("Entre 1 y 3 puntos, cada uno de una oración, sobre qué significa el cambio para el negocio del cliente")
        List<String> forYou,

        @JsonPropertyDescription("Entre 1 y 3 preguntas cortas que el cliente puede hacerle al estudiante antes de decidir, tuteando")
        List<String> questions
) {
}
