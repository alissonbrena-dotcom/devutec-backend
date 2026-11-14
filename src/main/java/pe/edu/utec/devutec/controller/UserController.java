package pe.edu.utec.devutec.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.devutec.dto.UserResponseDTO;
import pe.edu.utec.devutec.service.AvatarService;
import pe.edu.utec.devutec.service.UserService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AvatarService avatarService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    // Sube o reemplaza la foto de perfil: multipart/form-data con el campo "file" (JPG o PNG, máximo 5 MB).
    // Se guarda recortada cuadrada a 400 × 400. El administrador no puede cambiar la suya.
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDTO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(avatarService.upload(file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponseDTO> deleteAvatar() {
        return ResponseEntity.ok(avatarService.delete());
    }

    // Pública, porque una etiqueta <img> no manda el token. 404 si la persona no tiene foto.
    // Con ?v= (la URL que viene en avatarUrl) la imagen nunca cambia, así que se puede guardar un día entero.
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id, @RequestParam(required = false) String v) {
        AvatarService.AvatarImage avatar = avatarService.find(id);
        CacheControl cache = v != null ? CacheControl.maxAge(1, TimeUnit.DAYS) : CacheControl.maxAge(5, TimeUnit.MINUTES);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(cache.cachePublic())
                .body(avatar.bytes());
    }
}
