package pe.edu.utec.devutec.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProposalExplanationRepository extends JpaRepository<ProposalExplanation, Long> {

    Optional<ProposalExplanation> findByApplication_Id(Long applicationId);
}
