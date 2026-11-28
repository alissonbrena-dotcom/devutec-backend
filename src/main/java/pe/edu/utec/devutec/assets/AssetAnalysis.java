package pe.edu.utec.devutec.assets;

import java.util.List;

// Lo que la IA devuelve al mirar un archivo del cliente (salida estructurada)
public record AssetAnalysis(
        // LOGO, FOTO_PRODUCTO, FOTO_LOCAL, MENU_O_CATALOGO, DOCUMENTO u OTRO
        String type,
        // Una o dos frases: qué es y qué sirve para el proyecto
        String summary,
        // Colores principales en hexadecimal (#RRGGBB), si se ven
        List<String> colors
) {
}
