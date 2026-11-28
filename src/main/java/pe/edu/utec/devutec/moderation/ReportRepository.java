package pe.edu.utec.devutec.moderation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"reporter", "reported"})
    List<Report> findTop100ByOrderByCreatedAtDesc();

    long countByReportedId(Long reportedId);
}
