package pe.edu.utec.devutec.translation;

import java.util.List;

// Lo que devuelve la IA, ya revisado. Según la dirección se llenan unos campos u otros:
// FOR_CLIENT → enSimple, yaEsta, falta, revisaEsto y terminos
// FOR_STUDENT → enSimple, tareas, preguntas y ojo
public record TranslationContent(
        String enSimple,
        List<String> yaEsta,
        List<String> falta,
        List<String> revisaEsto,
        List<Term> terminos,
        List<String> tareas,
        List<String> preguntas,
        String ojo
) {
    // Una palabra técnica que usó el estudiante y lo que significa, en una línea
    public record Term(String termino, String significado) {
    }
}
