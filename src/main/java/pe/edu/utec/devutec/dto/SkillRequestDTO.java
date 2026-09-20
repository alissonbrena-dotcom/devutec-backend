package pe.edu.utec.devutec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequestDTO {

    @NotBlank(message = "El nombre de la skill es obligatorio")
    private String name;
}