package pe.edu.utec.devutec.guide;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.devutec.model.contract.ContractStage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Mini guía de una etapa para el estudiante: qué hacer (tareas con horas), qué preguntarle al cliente,
// riesgos y un consejo. La IA la arma una sola vez por etapa; después solo se marcan tareas.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stage_guides")
public class StageGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false, unique = true)
    private ContractStage stage;

    @OneToMany(mappedBy = "guide", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<StageTask> tasks = new ArrayList<>();

    // Listas guardadas como JSON (son cortas y solo se leen junto con la guía)
    @Column(name = "client_questions", nullable = false, columnDefinition = "TEXT")
    private String clientQuestions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String risks;

    @Column(length = 300)
    private String tip;

    // Horas de referencia de la etapa según la tabla de horas (null si el proyecto no tiene funcionalidades)
    @Column(name = "reference_hours_min")
    private Integer referenceHoursMin;

    @Column(name = "reference_hours_max")
    private Integer referenceHoursMax;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void addTask(StageTask task) {
        task.setGuide(this);
        tasks.add(task);
    }
}
