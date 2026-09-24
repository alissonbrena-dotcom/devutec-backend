package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.ReviewCreateDTO;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;
import pe.edu.utec.devutec.exceptions.DuplicateResourceException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.mapper.ReviewMapper;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.model.review.Review;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.ReviewRepository;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ReviewMapper reviewMapper;

    @Override
    @Transactional
    public ReviewResponseDTO create(Long contractId, ReviewCreateDTO dto) {
        Contract contract = findContract(contractId);
        User author = currentUserService.getCurrentUser();
        User reviewee = resolveReviewee(contract, author);
        ensureIsConfirmed(contract);
        ensureNotReviewedYet(contract, author);

        Review review = new Review();
        review.setContract(contract);
        review.setAuthor(author);
        review.setReviewee(reviewee);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    private Contract findContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
    }

    private User resolveReviewee(Contract contract, User author) {
        User freelancer = contract.getApplication().getFreelancer().getUser();
        Long clientId = contract.getApplication().getProject().getClientId();

        if (author.getId().equals(clientId)) {
            return freelancer;
        }
        if (author.getId().equals(freelancer.getId())) {
            return userRepository.findById(clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + clientId));
        }
        throw new ForbiddenOperationException("Solo el cliente o el freelancer del contrato pueden reseñarlo");
    }

    private void ensureIsConfirmed(Contract contract) {
        if (contract.getStatus() != ContractStatus.CONFIRMED) {
            throw new InvalidOperationException("Solo se puede reseñar un contrato con la entrega confirmada");
        }
    }

    private void ensureNotReviewedYet(Contract contract, User author) {
        if (reviewRepository.existsByContract_IdAndAuthor_Id(contract.getId(), author.getId())) {
            throw new DuplicateResourceException("Ya dejaste una reseña para este contrato");
        }
    }
}
