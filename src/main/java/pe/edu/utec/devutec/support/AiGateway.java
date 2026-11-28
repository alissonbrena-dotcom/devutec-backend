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

    // Una imagen para que la IA la mire (ya revisada y vuelta a generar por el backend). mediaType: image/png o image/jpeg
    record ImageInput(String mediaType, byte[] data) {
    }

    // Igual que replyStructured, pero el mensaje del usuario lleva imágenes (por ejemplo, el logo del negocio).
    // Solo Claude lo soporta; los demás proveedores responden "no disponible" y el backend sigue sin el análisis.
    default <T> T replyStructuredWithImages(String instructions, String text, List<ImageInput> images, Class<T> type) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El análisis de imágenes no está disponible en este momento");
    }
}
