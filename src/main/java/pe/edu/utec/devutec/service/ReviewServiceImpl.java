package pe.edu.utec.devutec.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.dto.PageResponseDTO;
import pe.edu.utec.devutec.dto.ReviewCreateDTO;
import pe.edu.utec.devutec.dto.ReviewResponseDTO;
import pe.edu.utec.devutec.events.ReviewCreatedEvent;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

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
        Review saved = reviewRepository.save(review);

        eventPublisher.publishEvent(new ReviewCreatedEvent(this, saved.getId(), reviewee.getId()));
        return reviewMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponseDTO> findByContract(Long contractId) {
        Contract contract = findContract(contractId);
        ensureIsParticipant(contract, currentUserService.getCurrentUser());
        return reviewRepository.findByContract_IdOrderByCreatedAtDesc(contractId).stream()
                .map(reviewMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ReviewResponseDTO> findByReviewee(Long userId, Integer minRating, int page, int size) {
        ensureUserExists(userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponseDTO.from(findReceivedReviews(userId, minRating, pageable).map(reviewMapper::toResponse));
    }

    private Page<Review> findReceivedReviews(Long userId, Integer minRating, Pageable pageable) {
        if (minRating == null) {
            return reviewRepository.findByReviewee_Id(userId, pageable);
        }
        return reviewRepository.findByReviewee_IdAndRatingGreaterThanEqual(userId, minRating, pageable);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + userId);
        }
    }

    private Contract findContract(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
    }

    private User resolveReviewee(Contract contract, User author) {
        ensureIsParticipant(contract, author);
        if (isClient(contract, author)) {
            return freelancerOf(contract);
        }
        Long clientId = clientIdOf(contract);
        return userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + clientId));
    }

    private void ensureIsParticipant(Contract contract, User user) {
        if (!isClient(contract, user) && !isFreelancer(contract, user)) {
            throw new ForbiddenOperationException("Solo el cliente o el freelancer del contrato tienen acceso a sus reseñas");
        }
    }

    private boolean isClient(Contract contract, User user) {
        return user.getId().equals(clientIdOf(contract));
    }

    private boolean isFreelancer(Contract contract, User user) {
        return user.getId().equals(freelancerOf(contract).getId());
    }

    private Long clientIdOf(Contract contract) {
        return contract.getApplication().getProject().getClientId();
    }

    private User freelancerOf(Contract contract) {
        return contract.getApplication().getFreelancer().getUser();
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
