package pe.edu.utec.devutec.guide;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Una tarea del checklist de una etapa, con sus horas estimadas
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stage_tasks")
public class StageTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id", nullable = false)
    private StageGuide guide;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Integer hours;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private LocalDateTime doneAt;
}
