package pe.edu.utec.devutec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Foto de perfil de un usuario: JPEG de 400 × 400 (unos 40 KB). Una fila por usuario, con su mismo id.
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private byte[] image;
}
