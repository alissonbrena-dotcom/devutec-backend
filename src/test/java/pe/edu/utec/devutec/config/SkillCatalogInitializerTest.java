package pe.edu.utec.devutec.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.repository.SkillRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillCatalogInitializerTest {

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private SkillCatalogInitializer initializer;

    // TEST 1: con la tabla vacía se agrega el catálogo completo
    @Test
    @SuppressWarnings("unchecked")
    void run_sinHabilidades_deberiaAgregarElCatalogo() {
        when(skillRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        initializer.run(null);

        ArgumentCaptor<List<Skill>> saved = ArgumentCaptor.forClass(List.class);
        verify(skillRepository).saveAll(saved.capture());
        assertEquals(SkillCatalogInitializer.CATALOG.size(), saved.getValue().size());
    }

    // TEST 2: las que ya existen no se vuelven a crear
    @Test
    @SuppressWarnings("unchecked")
    void run_conAlgunasHabilidades_deberiaAgregarSoloLasQueFaltan() {
        when(skillRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(skillRepository.existsByNameIgnoreCase("React")).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCase("Java")).thenReturn(true);

        initializer.run(null);

        ArgumentCaptor<List<Skill>> saved = ArgumentCaptor.forClass(List.class);
        verify(skillRepository).saveAll(saved.capture());
        List<String> names = saved.getValue().stream().map(Skill::getName).toList();
        assertEquals(SkillCatalogInitializer.CATALOG.size() - 2, names.size());
        assertFalse(names.contains("React"));
        assertFalse(names.contains("Java"));
    }

    // TEST 3: si ya están todas, no se guarda nada
    @Test
    void run_conTodasLasHabilidades_noDeberiaGuardar() {
        when(skillRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        initializer.run(null);

        verify(skillRepository, never()).saveAll(anyList());
    }
}
