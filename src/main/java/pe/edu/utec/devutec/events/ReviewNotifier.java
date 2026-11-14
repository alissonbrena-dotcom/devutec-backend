package pe.edu.utec.devutec.events;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.repository.ReviewRepository;
import pe.edu.utec.devutec.service.EmailService;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReviewNotifier {

    private final EmailService emailService;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final EmailLinks links;

    @Async
    @TransactionalEventListener
    public void onReviewCreated(ReviewCreatedEvent event) {
        // El correo se manda fuera de la transacción: de la reseña solo se leen el comentario y el id del contrato,
        // y el rol se busca por id para no tocar relaciones LAZY
        var review = reviewRepository.findById(event.getReviewId());
        boolean isFreelancer = userRepository.findById(event.getRevieweeId())
                .map(user -> user.getRol() == Role.FREELANCER)
                .orElse(false);

        // Un freelancer va a su perfil público, donde se ven sus reseñas; un cliente, al contrato
        String actionUrl = isFreelancer
                ? links.to("/freelancers/" + event.getRevieweeId())
                : review.map(r -> links.to("/contracts/" + r.getContract().getId())).orElse(links.to("/contracts"));

        // HashMap porque el comentario puede venir vacío (Map.of no acepta null)
        Map<String, Object> variables = new HashMap<>();
        variables.put("revieweeName", event.getRevieweeName());
        variables.put("authorName", event.getAuthorName());
        variables.put("projectTitle", event.getProjectTitle());
        variables.put("rating", event.getRating());
        variables.put("comment", review.map(r -> r.getComment()).orElse(null));
        variables.put("appUrl", links.base());
        variables.put("actionUrl", actionUrl);
        variables.put("actionLabel", isFreelancer ? "Ver mi perfil" : "Ver la reseña");

        emailService.sendHtmlEmail(
                event.getRevieweeEmail(),
                event.getAuthorName() + " te dejó una reseña",
                "review-received",
                variables
        );
    }
}
