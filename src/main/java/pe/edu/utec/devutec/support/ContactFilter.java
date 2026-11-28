package pe.edu.utec.devutec.support;

import java.util.List;
import java.util.regex.Pattern;

// Evita que cliente y estudiante se pasen datos de contacto y se vayan de DEVUTEC (pierden la garantía y las reseñas).
// Detecta datos (celulares, teléfonos con +51, correos) y frases para sacar la conversación afuera
// ("escríbeme al wsp", "mi número es..."). La palabra "WhatsApp" sola SÍ se permite: un proyecto puede necesitar
// un "botón de WhatsApp" en la web. Los datos reales para publicar van en "Datos del negocio" del contrato.
public final class ContactFilter {

    public static final String MESSAGE = "Por tu seguridad, no compartas datos de contacto: la garantía de DEVUTEC "
            + "solo te protege dentro de la plataforma. Si es un dato para poner en la web, agrégalo en "
            + "\"Datos del negocio\" del contrato.";

    private static final List<Pattern> CONTACT_DATA = List.of(
            // Celular peruano: 9 dígitos que empiezan con 9, con o sin +51 y con espacios, puntos o guiones
            Pattern.compile("(?<!\\d)(?:\\+?\\s*51[\\s.-]*)?9(?:[\\s.-]?\\d){8}(?!\\d)"),
            // Cualquier número con código de país: +51 1 234 5678, +1 555...
            Pattern.compile("\\+\\s*\\d{1,3}[\\s.-]*\\(?\\d{1,4}\\)?(?:[\\s.-]?\\d){6,}"),
            // Correo, también escrito "nombre arroba gmail punto com"
            Pattern.compile("[\\w.+-]+\\s*(?:@|\\(at\\)|\\barroba\\b)\\s*[\\w-]+\\s*(?:\\.|\\bpunto\\b)\\s*[a-z]{2,}",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));

    private static final List<Pattern> LEAVE_INTENT = List.of(
            // "escríbeme / háblame / llámame / agrégame / hablemos ... por wsp, al cel, a mi correo"
            Pattern.compile("\\b(?:escr[ií]beme|escr[ií]banme|h[aá]blame|ll[aá]mame|agr[eé]game|cont[aá]ctame|"
                            + "hablemos|conversemos|te escribo|te llamo|me escribes|p[aá]same tu)\\b.{0,40}?"
                            + "\\b(?:whats\\s*app|wh?atsap+|wasap+|wsp|wpp|telegram|signal|correo|mail|cel|celular|"
                            + "n[uú]mero|tel[eé]fono|telf|privado|interno|dm)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
            // "mi número / mi cel / mi correo / mi wsp"
            Pattern.compile("\\bmi\\s+(?:n[uú]mero|cel|celular|tel[eé]fono|telf|correo|mail|whats\\s*app|wsp|wasap|telegram)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
            // "por fuera de la plataforma", "sin DEVUTEC"
            Pattern.compile("\\b(?:por\\s+fuera|fuera\\s+de\\s+(?:la\\s+)?(?:plataforma|devutec|la\\s+app|la\\s+p[aá]gina)|sin\\s+devutec)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));

    private ContactFilter() {
    }

    // true si el texto tiene un dato de contacto o pide hablar por fuera
    public static boolean hasContact(String text) {
        if (text == null || text.isBlank()) return false;
        return CONTACT_DATA.stream().anyMatch(p -> p.matcher(text).find())
                || LEAVE_INTENT.stream().anyMatch(p -> p.matcher(text).find());
    }

    // Quita solo los datos (números y correos) y deja el resto del texto. Se usa con lo que escribe la IA.
    public static String removeContactData(String text) {
        if (text == null) return null;
        String clean = text;
        for (Pattern pattern : CONTACT_DATA) {
            clean = pattern.matcher(clean).replaceAll("[dato de contacto]");
        }
        return clean;
    }
}
