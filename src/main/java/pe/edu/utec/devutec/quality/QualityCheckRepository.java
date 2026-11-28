package pe.edu.utec.devutec.quality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QualityCheckRepository extends JpaRepository<QualityCheck, Long> {

    Optional<QualityCheck> findFirstByStage_IdOrderByCreatedAtDesc(Long stageId);
}
