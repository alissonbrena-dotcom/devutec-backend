package pe.edu.utec.devutec.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Métricas activadas para la web que se entrega en un contrato. Se crea cuando el cliente da su consentimiento.
// publicKey va dentro del script de la web: no es secreta, solo dice a qué contrato pertenecen los datos.
@Entity
@Table(name = "metrics_sites")
@Getter
@Setter
@NoArgsConstructor
public class MetricsSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, unique = true)
    private Long contractId;

    @Column(name = "public_key", nullable = false, unique = true, length = 32)
    private String publicKey;

    // Quién aceptó (el cliente del contrato), cuándo y qué versión del texto de consentimiento leyó
    @Column(name = "consented_by_user_id", nullable = false)
    private Long consentedByUserId;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    // Dominio de la web del cliente (por ejemplo https://pizzeriaramon.pe). Se fija con el primer dato que no es de prueba;
    // después solo se aceptan datos de ese dominio (y de los de prueba: localhost y links de vista previa)
    @Column(name = "allowed_origin")
    private String allowedOrigin;

    // Primer y último dato recibido (de prueba o real). El primero desbloquea la Entrega final
    @Column(name = "first_event_at")
    private LocalDateTime firstEventAt;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    // Los 3 meses gratis empiezan con el primer dato real (no de prueba), así no se gastan mientras el estudiante desarrolla
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MetricsPlan plan = MetricsPlan.NONE;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "plan_started_at")
    private LocalDateTime planStartedAt;
}
