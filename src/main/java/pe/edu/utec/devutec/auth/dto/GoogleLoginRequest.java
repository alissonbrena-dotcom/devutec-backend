package pe.edu.utec.devutec.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pe.edu.utec.devutec.auth.domain.Role;

@Getter
@Setter
public class GoogleLoginRequest {

    @NotBlank(message = "El token de Google es obligatorio")
    private String idToken;

    // Solo hace falta la primera vez, cuando la cuenta todavía no existe en DEVUTEC
    private Role rol;
}
