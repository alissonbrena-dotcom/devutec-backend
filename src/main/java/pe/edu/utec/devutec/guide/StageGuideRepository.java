package pe.edu.utec.devutec.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StageGuideRepository extends JpaRepository<StageGuide, Long> {

    Optional<StageGuide> findByStage_Id(Long stageId);
}
