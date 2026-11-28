package pe.edu.utec.devutec.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Horas de referencia de una funcionalidad típica (por ejemplo "Carta o catálogo": 6 a 10 horas).
// La IA solo dice qué funcionalidades tiene un proyecto usando estos códigos; las horas y el precio los calcula
// el backend con esta tabla, así ningún número sale inventado por la IA. Se puede ajustar sin volver a desplegar.
@Entity
@Table(name = "funcionalidades_referencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FuncionalidadReferencia {

    // Código fijo que usa la IA, por ejemplo CARTA_CATALOGO
    @Id
    @Column(length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    // WEB, AUTOMATIZACION, APP o GENERAL (sirve para cualquier tipo de proyecto)
    @Column(nullable = false, length = 20)
    private String categoria;

    @Column(name = "horas_min", nullable = false)
    private int horasMin;

    @Column(name = "horas_max", nullable = false)
    private int horasMax;
}
