package pe.edu.utec.devutec.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectFilterDTOTest {

    // TEST 1: sin habilidades no se filtra por habilidad
    @Test
    void allSkillIds_sinHabilidades_deberiaEstarVacio() {
        ProjectFilterDTO filters = new ProjectFilterDTO();

        assertTrue(filters.allSkillIds().isEmpty());
    }

    // TEST 2: el parámetro de antes (skillId) sigue funcionando solo
    @Test
    void allSkillIds_soloSkillId_deberiaUsarlo() {
        ProjectFilterDTO filters = new ProjectFilterDTO();
        filters.setSkillId(3L);

        assertEquals(Set.of(3L), filters.allSkillIds());
    }

    // TEST 3: skillIds y skillId se juntan sin repetir y se ignoran los nulos
    @Test
    void allSkillIds_ambos_deberiaJuntarlosSinRepetidos() {
        ProjectFilterDTO filters = new ProjectFilterDTO();
        filters.setSkillId(3L);
        filters.setSkillIds(Arrays.asList(1L, 3L, null, 2L));

        assertEquals(List.of(3L, 1L, 2L), List.copyOf(filters.allSkillIds()));
    }
}
