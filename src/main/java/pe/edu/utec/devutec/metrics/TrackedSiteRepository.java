package pe.edu.utec.devutec.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackedSiteRepository extends JpaRepository<TrackedSite, Long> {

    Optional<TrackedSite> findByContract_Id(Long contractId);

    Optional<TrackedSite> findBySiteKey(String siteKey);
}
