package pe.edu.utec.devutec.model.contract;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.edu.utec.devutec.model.application.Application;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @OneToOne
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime deliveredAt;

    public Contract () {

    }

    public Contract (Long id, Project project, Application application, ContractStatus status, LocalDateTime startedAt, LocalDateTime deliveredAt) {
        this.id = id;
        this.project = project;
        this.application = application;
        this.status = status;
        this.startedAt = startedAt;
        this.deliveredAt = deliveredAt
    }

}
