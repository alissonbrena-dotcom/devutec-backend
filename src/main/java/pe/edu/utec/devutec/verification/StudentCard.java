package pe.edu.utec.devutec.verification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Foto del carnet universitario de un estudiante. Es privada: solo la ven la persona y el admin.
// Una fila por usuario, con su mismo id (al subir otra, se reemplaza).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_cards")
public class StudentCard {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private byte[] image;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CarnetStatus status;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
