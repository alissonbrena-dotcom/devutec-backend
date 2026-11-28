package pe.edu.utec.devutec.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.application.Application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal budget;

    private LocalDate deadline;

    @Column(length = 100)
    private String location;

    @Column(name = "client_id")
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(name = "ai_generated_brief", columnDefinition = "TEXT")
    private String aiGeneratedBrief;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_skills",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new HashSet<>();

    // Tipo de proyecto: WEB, AUTOMATIZACION o APP (lo define el asistente IA; puede ser null)
    @Column(length = 20)
    private String category;

    // Funcionalidades que detectó el asistente IA, para estimar horas y precio
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_features", joinColumns = @JoinColumn(name = "project_id"))
    private List<ProjectFeature> features = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // El cliente quiere ver las métricas de su web en DEVUTEC (lo pregunta el asistente o la casilla del formulario).
    // El estudiante lo ve antes de postular: integrar el script es un requisito del cliente.
    @Column(name = "metrics_requested")
    private Boolean metricsRequested = false;

    // Al eliminar un proyecto se eliminan sus postulaciones; ProjectServiceImpl solo lo permite si está OPEN y sin contratos
    @OneToMany(mappedBy = "project", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Application> applications = new ArrayList<>();
}