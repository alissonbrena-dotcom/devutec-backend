package pe.edu.utec.devutec.support;

import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.util.List;

// Conversación con el modelo de IA. Es una interfaz para poder probar el servicio sin llamar a la IA de verdad
// y para cambiar de proveedor (Claude o Gemini) sin tocar el resto del backend.
public interface AiGateway {

    // instructions: reglas y conocimiento del asistente · turns: la conversación, terminando en la pregunta nueva
    String reply(String instructions, List<ChatTurnDTO> turns);

    // Igual que reply, pero la IA responde con un JSON que sigue la forma de la clase type (salidas estructuradas).
    // Así el backend recibe campos fijos (título, funcionalidades, si la pregunta está fuera de tema...) y no texto libre.
    default <T> T replyStructured(String instructions, List<ChatTurnDTO> turns, Class<T> type) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El asistente de proyectos no está disponible en este momento");
    }
}
