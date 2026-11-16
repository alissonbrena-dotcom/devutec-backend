package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.Size;
import pe.edu.utec.devutec.auth.domain.User;

// Redes y página web del negocio. Se usa para guardarlas (PUT /users/me/links) y para mostrarlas:
// en /users/me y en el detalle del proyecto (clientLinks). Cada campo es null si no la tiene.
public record SocialLinksDTO(
        @Size(max = 255, message = "El link de Instagram es demasiado largo") String instagram,
        @Size(max = 255, message = "El link de Facebook es demasiado largo") String facebook,
        @Size(max = 255, message = "El link de TikTok es demasiado largo") String tiktok,
        @Size(max = 255, message = "La página web es demasiado larga") String sitioWeb
) {
    public static SocialLinksDTO of(User user) {
        return new SocialLinksDTO(user.getInstagram(), user.getFacebook(), user.getTiktok(), user.getSitioWeb());
    }
}
