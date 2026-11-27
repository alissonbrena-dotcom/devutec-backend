package pe.edu.utec.devutec.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una funcionalidad del proyecto (código de la tabla de horas de referencia) y cuántas veces aparece.
// La guarda el asistente IA al crear el proyecto; con ella el estudiante ve las horas estimadas antes de postular.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFeature {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
