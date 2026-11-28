package pe.edu.utec.devutec.assistant;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// Forma fija de la respuesta de la IA (salidas estructuradas): el backend recibe estos campos, no texto libre.
// Los precios NO vienen de la IA: los calcula PriceEstimator con la tabla de horas y los proyectos terminados.
public record ProjectAssistantAiReply(
        @JsonPropertyDescription("true si el último mensaje del cliente no tiene que ver con crear su proyecto digital en DEVUTEC")
        boolean outOfScope,

        @JsonPropertyDescription("Lo que le dices al cliente, en español, cercano y simple, máximo 3 oraciones. Si falta información, una sola pregunta. Nunca escribas precios ni montos.")
        String message,

        @JsonPropertyDescription("true cuando ya sabes el tipo de negocio, qué quiere lograr y qué funcionalidades necesita")
        boolean ready,

        @JsonPropertyDescription("Título corto del proyecto, por ejemplo: Web con carta y pedidos para Pizzería Mario. Vacío si aún no está listo.")
        String title,

        @JsonPropertyDescription("Descripción del proyecto en lenguaje simple, sin términos técnicos, para que el cliente la entienda. Vacía si aún no está listo.")
        String clientDescription,

        @JsonPropertyDescription("WEB, AUTOMATIZACION o APP. Vacío si aún no está claro.")
        String category,

        @JsonPropertyDescription("Habilidades técnicas que necesita el estudiante, elegidas SOLO de la lista de habilidades disponibles")
        List<String> skills,

        @JsonPropertyDescription("Funcionalidades del proyecto, usando SOLO los códigos de la tabla de funcionalidades")
        List<AiFeature> features,

        @JsonPropertyDescription("Presupuesto TOTAL en soles que dijo el cliente (ya incluye la tarifa de DEVUTEC). 0 si no lo dijo.")
        double clientBudget,

        @JsonPropertyDescription("Plazo en semanas que pidió el cliente. 0 si no lo dijo.")
        int weeks,

        @JsonPropertyDescription("Colores, logo o estilo de marca que mencionó el cliente. Vacío si no dijo nada.")
        String brandStyle,

        @JsonPropertyDescription("Qué contenido ya tiene el cliente (fotos, textos, carta, logo) y qué falta preparar. Vacío si no se habló.")
        String contentStatus,

        @JsonPropertyDescription("QUÉ datos de contacto del negocio quiere mostrar en la web o app (por ejemplo: botón de WhatsApp del negocio, correo, dirección, horario, Instagram). NUNCA números, correos ni links: esos los pasa en el contrato. Vacío si no aplica.")
        String contactToShow,

        @JsonPropertyDescription("Dudas que quedaron abiertas y que el estudiante debe confirmar con el cliente en la etapa de Descubrimiento, como preguntas cortas. Lista vacía si no queda ninguna.")
        List<String> openQuestions,

        @JsonPropertyDescription("true si el cliente dijo que sí quiere ver las métricas de su web o app en DEVUTEC (visitas, contactos, pedidos). false si dijo que no o todavía no se le preguntó.")
        boolean wantsMetrics
) {
    public record AiFeature(
            @JsonPropertyDescription("Código de la tabla de funcionalidades, por ejemplo CARTA_CATALOGO")
            String code,
            @JsonPropertyDescription("Cuántas veces aparece, por ejemplo 3 páginas informativas = 3")
            int quantity
    ) {
    }
}
