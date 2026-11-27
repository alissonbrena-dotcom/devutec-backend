package pe.edu.utec.devutec.translation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranslationRepository extends JpaRepository<Translation, Long> {

    Optional<Translation> findFirstByContract_IdAndDirectionAndOriginalHash(
            Long contractId, TranslationDirection direction, String originalHash);
}
