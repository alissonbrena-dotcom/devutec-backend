package pe.edu.utec.devutec.model.application;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "freelancer_id"}))
public class Application {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name = "freelancer_id")
    private FreelancerProfile freelancer;

    @Column(nullable = false)
    private String message;

    private BigDecimal proposedPrice;

    @Enumerated(EnumType.STRING)
    private AppStatus status;

    private LocalDateTime createdAt;

    public Application() {

    }

    public Application (Long id, Project project, FreelancerProfile freelancer, String message, BigDecimal proposedPrice, AppStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.project = project;
        this.freelancer = freelancer;
        this.message = message;
        this.proposedPrice = proposedPrice;
        this.status = status;
        this.createdAt = createdAt;
    }
}
