package pe.edu.utec.devutec.support;

import java.util.List;

// Conversación con el modelo de IA. Es una interfaz para poder probar el servicio sin llamar a la IA de verdad.
public interface AiGateway {

    // instructions: reglas y conocimiento del asistente · turns: la conversación, terminando en la pregunta nueva
    String reply(String instructions, List<ChatTurnDTO> turns);
}
