package pe.edu.utec.devutec.support;

// reply: la respuesta del asistente · remaining: cuántas preguntas le quedan hoy a la persona
public record ChatResponseDTO(String reply, int remaining) {
}
