package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.UserAvatar;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
