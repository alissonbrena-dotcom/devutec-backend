package pe.edu.utec.devutec.assistant;

import com.anthropic.models.messages.MessageCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.pricing.CommissionCalculator;
import pe.edu.utec.devutec.pricing.FuncionalidadReferenciaRepository;
import pe.edu.utec.devutec.pricing.PaymentPlanner;
import pe.edu.utec.devutec.pricing.PriceEstimateDTO;
import pe.edu.utec.devutec.pricing.PriceEstimator;
import pe.edu.utec.devutec.repository.SkillRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAssistantServiceTest {

    // IA falsa: devuelve la respuesta que le pongamos y guarda lo que recibió
    static class FakeAi implements AiGateway {
        Object next;
        String instructions;
        List<ChatTurnDTO> turns;

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T replyStructured(String instructions, List<ChatTurnDTO> turns, Class<T> type) {
            this.instructions = instructions;
            this.turns = turns;
            return type.cast(next);
        }
    }

    private final FakeAi ai = new FakeAi();
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final SkillRepository skills = mock(SkillRepository.class);
    private final FuncionalidadReferenciaRepository funcionalidades = mock(FuncionalidadReferenciaRepository.class);
    private final PriceEstimator estimator = mock(PriceEstimator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T15:00:00Z"), ZoneId.of("America/Lima"));
    private ProjectAssistantService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);
        user.setRol(Role.CLIENT);
        when(currentUser.getCurrentUser()).thenReturn(user);
        when(skills.findAll()).thenReturn(List.of(new Skill(1L, "React"), new Skill(2L, "Diseño web")));
        when(funcionalidades.findAllByOrderByCategoriaAscCodigoAsc()).thenReturn(List.of());
        when(estimator.estimate(any(), any())).thenReturn(new PriceEstimateDTO("HOURS", 0, "Estimación", List.of(
                new PriceEstimateDTO.Feature("CARTA_CATALOGO", "Carta", 1, 6, 10)), 20, 40, new BigDecimal("25"),
                new BigDecimal("500.00"), new BigDecimal("1000.00"), new BigDecimal("550.00"), new BigDecimal("1100.00"),
                2, 4, List.of(new PriceEstimateDTO.Stage("Descubrimiento", 10, 2, 4),
                new PriceEstimateDTO.Stage("Diseño", 30, 6, 12), new PriceEstimateDTO.Stage("Desarrollo", 40, 8, 16),
                new PriceEstimateDTO.Stage("Entrega final", 20, 4, 8))));
        service = new ProjectAssistantService(ai, currentUser, skills, funcionalidades, estimator,
                new CommissionCalculator(new BigDecimal("0.10")), new PaymentPlanner(), 2, clock);
    }

    private static ProjectAssistantAiReply reply(boolean outOfScope, boolean ready, double budget) {
        return new ProjectAssistantAiReply(outOfScope, "¿Qué negocio tienes?", ready, "Web para Pizzería Mario",
                "Una web con tu carta y pedidos.", "WEB", List.of("react", "Inventada"),
                List.of(new ProjectAssistantAiReply.AiFeature("CARTA_CATALOGO", 1)), budget, 3, "rojo y crema");
    }

    @Test
    void fueraDeTema_respondeUnMensajeFijoSinBorrador() {
        ai.next = reply(true, false, 0);

        ProjectAssistantResponseDTO response = service.chat(new ProjectAssistantRequestDTO("Hazme la tarea de física", null));

        assertTrue(response.outOfScope());
        assertEquals(ProjectAssistantService.OUT_OF_SCOPE_REPLY, response.reply());
        assertNull(response.draft());
    }

    @Test
    void sinInformacionSuficiente_soloPregunta() {
        ai.next = reply(false, false, 0);

        ProjectAssistantResponseDTO response = service.chat(new ProjectAssistantRequestDTO("Hola", null));

        assertFalse(response.ready());
        assertEquals("¿Qué negocio tienes?", response.reply());
        assertNull(response.draft());
        assertTrue(ai.instructions.contains("outOfScope"));
        assertTrue(ai.instructions.contains("React, Diseño web"));
    }

    @Test
    void listo_armaElBorradorConComisionEtapasYHabilidadesValidas() {
        ai.next = reply(false, true, 880);

        ProjectAssistantResponseDTO response = service.chat(new ProjectAssistantRequestDTO("Listo", List.of(
                new ChatTurnDTO("assistant", "Hola, ¿en qué te ayudo?"), new ChatTurnDTO("user", "Tengo una pizzería"))));

        ProjectAssistantResponseDTO.Draft draft = response.draft();
        assertTrue(response.ready());
        // La historia arranca con el primer mensaje del cliente
        assertEquals("user", ai.turns.get(0).role());
        assertEquals(List.of("React"), draft.skillNames());
        assertEquals(new BigDecimal("800.00"), draft.budget().freelancerAmount());
        assertEquals(new BigDecimal("80.00"), draft.budget().commission());
        assertEquals(new BigDecimal("240.00"), draft.stages().get(1).freelancerAmount());
        assertEquals(new BigDecimal("264.00"), draft.stages().get(1).clientAmount());
        assertEquals(LocalDate.of(2026, 12, 18), draft.deadline());
        assertTrue(draft.description().contains("rojo y crema"));
        // Pago en dos partes sobre el presupuesto del cliente (S/ 880 = S/ 800 para el estudiante)
        assertEquals(2, draft.payments().size());
        assertEquals(new BigDecimal("440.00"), draft.payments().get(0).clientAmount());
        assertEquals(new BigDecimal("400.00"), draft.payments().get(1).freelancerAmount());
    }

    @Test
    void limiteDiario_respondeTooManyRequests() {
        ai.next = reply(false, false, 0);
        service.chat(new ProjectAssistantRequestDTO("1", null));
        service.chat(new ProjectAssistantRequestDTO("2", null));

        ApiException ex = assertThrows(ApiException.class, () -> service.chat(new ProjectAssistantRequestDTO("3", null)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void elFormatoJsonParaClaudeSeGeneraSinErrores() {
        // El SDK arma el esquema JSON desde el record y lo valida localmente al construir la petición
        assertDoesNotThrow(() -> MessageCreateParams.builder()
                .model("claude-opus-5-5")
                .maxTokens(1024L)
                .outputConfig(ProjectAssistantAiReply.class)
                .addUserMessage("Hola")
                .build());
    }
}
