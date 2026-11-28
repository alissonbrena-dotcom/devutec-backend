package pe.edu.utec.devutec.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Primera verificación: los estudiantes se registran con su correo universitario (termina en .edu.pe).
// Se puede apagar (REQUIRE_STUDENT_EMAIL=false) para cargar las cuentas de demo, que usan otros correos.
@Component
public class StudentEmailPolicy {

    private final boolean required;
    private final List<String> domains;

    public StudentEmailPolicy(@Value("${auth.require-student-email:true}") boolean required,
                              @Value("${auth.student-email-domains:.edu.pe}") String domains) {
        this.required = required;
        this.domains = Arrays.stream(domains.split(","))
                .map(String::trim)
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    // true si el correo es de una universidad (por ejemplo ana@utec.edu.pe o luis@pucp.edu.pe)
    public boolean isUniversityEmail(String email) {
        if (email == null) return false;
        String value = email.trim().toLowerCase(Locale.ROOT);
        int at = value.lastIndexOf('@');
        if (at < 0) return false;
        String host = value.substring(at + 1);
        return domains.stream().anyMatch(domain -> domain.startsWith(".") ? host.endsWith(domain) : host.equals(domain));
    }

    // Al registrar un estudiante (con contraseña o con Google)
    public void ensureStudentEmail(String email) {
        if (required && !isUniversityEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Para registrarte como estudiante usa tu correo universitario (el que termina en .edu.pe)");
        }
    }
}
