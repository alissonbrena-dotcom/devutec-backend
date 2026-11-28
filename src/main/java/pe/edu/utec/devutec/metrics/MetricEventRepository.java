package pe.edu.utec.devutec.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MetricEventRepository extends JpaRepository<MetricEvent, Long> {

    List<MetricEvent> findBySite_IdAndCreatedAtGreaterThanEqual(Long siteId, LocalDateTime from);

    Optional<MetricEvent> findFirstBySite_IdOrderByCreatedAtDesc(Long siteId);
}
