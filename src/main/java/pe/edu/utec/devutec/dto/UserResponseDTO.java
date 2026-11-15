package pe.edu.utec.devutec.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String nombre;
    private String email;
    private String rol;
    // Ruta de la foto de perfil (/api/v1/users/{id}/avatar?v=...) o null si no tiene
    private String avatarUrl;
    // Celular propio (9 dígitos) o null si no lo agregó
    private String celular;
}
