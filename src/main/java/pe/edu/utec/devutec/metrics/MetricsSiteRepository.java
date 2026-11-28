package pe.edu.utec.devutec.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetricsSiteRepository extends JpaRepository<MetricsSite, Long> {
    Optional<MetricsSite> findByContractId(Long contractId);

    Optional<MetricsSite> findByPublicKey(String publicKey);
}
