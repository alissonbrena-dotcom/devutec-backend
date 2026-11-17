package pe.edu.utec.devutec.service;

import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

// Redes y página web del negocio del cliente. Se aceptan como la gente las escribe ("@pizzeriadonmario",
// "pizzeriadonmario", "instagram.com/pizzeriadonmario" o el link completo) y se guardan como link completo
// con https://, para que el freelancer las abra con un toque. Vacío o null = no la tiene.
public final class SocialLinks {

    // Usuario de una red: letras, números, punto, guion y guion bajo
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9._-]{1,60}");
    private static final int MAX_LENGTH = 255;

    private SocialLinks() {
    }

    public static String instagram(String raw) {
        return social(raw, "instagram.com", "https://instagram.com/", "Instagram");
    }

    public static String facebook(String raw) {
        return social(raw, "facebook.com", "https://facebook.com/", "Facebook");
    }

    public static String tiktok(String raw) {
        return social(raw, "tiktok.com", "https://tiktok.com/@", "TikTok");
    }

    // Cualquier página web: se le agrega https:// si no lo tiene y tiene que tener un dominio (algo.pe, algo.com)
    public static String website(String raw) {
        return link(raw, "La página web no parece un link válido, por ejemplo pizzeriadonmario.pe");
    }

    // Cualquier link (por ejemplo, el de la entrega de un trabajo): agrega https:// si falta y revisa que tenga dominio
    public static String link(String raw, String errorMessage) {
        String value = clean(raw);
        if (value == null) {
            return null;
        }
        String url = value.matches("(?i)https?://.*") ? value : "https://" + value;
        String host = hostOf(url);
        if (host == null || !host.contains(".")) {
            throw invalid(errorMessage);
        }
        return url;
    }

    private static String social(String raw, String domain, String prefix, String name) {
        String value = clean(raw);
        if (value == null) {
            return null;
        }
        // Solo el usuario: "@pizzeria" o "pizzeria"
        String handle = value.startsWith("@") ? value.substring(1) : value;
        if (HANDLE.matcher(handle).matches() && !handle.contains(domain)) {
            return prefix + handle;
        }
        // Un link: tiene que ser de esa red (instagram.com, www.instagram.com, m.facebook.com...)
        String url = value.matches("(?i)https?://.*") ? value : "https://" + value;
        String host = hostOf(url);
        if (host == null || !(host.equals(domain) || host.endsWith("." + domain))) {
            throw invalid("El link de " + name + " tiene que ser de " + domain + " o solo tu usuario, por ejemplo @pizzeriadonmario");
        }
        return url;
    }

    private static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_LENGTH) {
            throw invalid("El link es demasiado largo");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid("Los links no pueden tener espacios");
        }
        return value;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
