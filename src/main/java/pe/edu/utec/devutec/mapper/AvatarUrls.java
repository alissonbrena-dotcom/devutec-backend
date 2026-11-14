package pe.edu.utec.devutec.mapper;

import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;

import java.time.ZoneOffset;

// URL de la foto de perfil que se manda en las respuestas, o null si la persona no tiene foto (la web y la app
// muestran sus iniciales). El ?v= cambia con cada foto nueva para que el navegador no muestre la anterior guardada.
public final class AvatarUrls {

    private AvatarUrls() {
    }

    public static String of(User user) {
        if (user.getRol() == Role.ADMIN) {
            return "/api/v1/users/" + user.getId() + "/avatar";
        }
        if (user.getAvatarUpdatedAt() == null) {
            return null;
        }
        return "/api/v1/users/" + user.getId() + "/avatar?v=" + user.getAvatarUpdatedAt().toEpochSecond(ZoneOffset.UTC);
    }
}
