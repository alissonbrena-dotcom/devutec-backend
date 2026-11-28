package pe.edu.utec.devutec.support;

import java.util.List;
import java.util.regex.Pattern;

// Evita que cliente y estudiante se salten DEVUTEC (y pierdan la garantía y las reseñas).
// - Datos de contacto: celulares, teléfonos con +51 y correos. Se bloquean ANTES del contrato (proyecto y postulación),
//   porque esos textos los ve cualquiera. Con el pago en garantía, el contacto se comparte en el contrato.
// - Intención de salirse: "escríbeme al wsp", "mi número", "por fuera de la plataforma" y, sobre todo, pagar por fuera
//   ("te pago por Yape", "sin la comisión"). Se bloquea SIEMPRE: lo que se protege es el pago.
// La palabra "WhatsApp" o "Yape" sola SÍ se permite: un proyecto puede pedir un botón de WhatsApp o cobrar con Yape.
public final class ContactFilter {

    public static final String CONTACT_MESSAGE = "Por tu seguridad, no compartas datos de contacto antes del contrato: "
            + "la garantía de DEVUTEC solo te protege dentro de la plataforma. Cuando el pago esté en garantía, "
            + "podrán escribirse por WhatsApp desde el contrato.";

    public static final String LEAVE_MESSAGE = "No se pueden acordar pagos ni trabajos por fuera de DEVUTEC: si algo "
            + "sale mal, nadie podría protegerte ni devolverte tu dinero. Si alguien te lo pide, repórtalo.";

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
            // Pagar por fuera: "te pago por Yape", "yapéame", "págame directo", "sin la comisión"
            Pattern.compile("\\b(?:yap[eé]ame|plin[eé]ame|te\\s+yapeo|te\\s+plineo|y[aá]peme|pl[ií]neme)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\b(?:te\\s+(?:pago|deposito|transfiero)|p[aá]game|"
                            + "te\\s+paso\\s+(?:la\\s+)?plata|te\\s+doy\\s+(?:la\\s+)?plata)\\b"
                            + ".{0,40}?\\b(?:yape|plin|transferencia|dep[oó]sito|efectivo|directo|aparte|por\\s+fuera|cuenta)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\b(?:sin|ahorrarnos|ahorrar|evitar|saltarnos)\\s+(?:la\\s+|el\\s+)?(?:comisi[oó]n|tarifa\\s+de\\s+devutec|plataforma)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS),
            // "por fuera de la plataforma", "lo arreglamos por fuera", "sin DEVUTEC"
            Pattern.compile("\\b(?:fuera\\s+de\\s+(?:la\\s+)?(?:plataforma|devutec|app|p[aá]gina)|sin\\s+devutec|"
                            + "(?:hablar|hablamos|hablemos|pagar|pago|pagarte|trabajar|trabajamos|coordinar|coordinamos|"
                            + "arreglar|arreglamos|arreglemos|seguir|seguimos|sigamos|negociar|negociemos)(?:lo|nos)?\\s+por\\s+fuera)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));

    private ContactFilter() {
    }

    // true si el texto tiene un dato de contacto (celular, +51, correo)
    public static boolean hasContactData(String text) {
        return text != null && CONTACT_DATA.stream().anyMatch(p -> p.matcher(text).find());
    }

    // true si el texto pide hablar, trabajar o pagar por fuera de DEVUTEC
    public static boolean hasLeaveIntent(String text) {
        return text != null && LEAVE_INTENT.stream().anyMatch(p -> p.matcher(text).find());
    }

    // true si tiene cualquiera de las dos cosas
    public static boolean hasContact(String text) {
        return hasContactData(text) || hasLeaveIntent(text);
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
