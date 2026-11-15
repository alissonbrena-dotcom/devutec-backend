package pe.edu.utec.devutec.service;

import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.util.regex.Pattern;

// Celulares de Perú: 9 dígitos que empiezan con 9. Se aceptan como la gente los escribe
// ("987 654 321", "987-654-321", "+51 987654321") y se guardan solo con los 9 dígitos.
public final class PhoneNumbers {

    private static final Pattern PERU_MOBILE = Pattern.compile("9\\d{8}");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+51")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0051")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("51") && digits.length() == 11) {
            digits = digits.substring(2);
        }
        if (!PERU_MOBILE.matcher(digits).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El celular tiene que tener 9 dígitos y empezar con 9, por ejemplo 987 654 321");
        }
        return digits;
    }
}
