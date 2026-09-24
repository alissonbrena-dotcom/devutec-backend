package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.auth.domain.FreelancerProfile;

import java.util.Optional;

public interface FreelancerProfileRepository extends JpaRepository<FreelancerProfile, Long> {
    Optional<FreelancerProfile> findByUserId(Long userId);

}
