package pe.edu.utec.devutec.proposal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.repository.ApplicationRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProposalExplanationServiceTest {

    private static final String AI_REPLY = """
            ```json
            {"reason": "Sofía ya tiene una plantilla de carta que puede reutilizar, por eso cobra menos.",
             "forYou": ["Pagarías S/ 55 menos.", "  ", "Tendrías la web en la misma fecha."],
             "questions": ["¿La plantilla se puede adaptar a mis colores?"]}
            ```""";

    // IA falsa como Gemini: no soporta salidas estructuradas, así que se usa reply y se lee el JSON
    static class FakeAi implements AiGateway {
        String instructions;
        List<ChatTurnDTO> turns;
        int calls;
        String answer = AI_REPLY;

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            this.instructions = instructions;
            this.turns = turns;
            calls++;
            return answer;
        }
    }

    private final FakeAi ai = new FakeAi();
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ProposalExplanationRepository explanations = mock(ProposalExplanationRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-28T15:00:00Z"), ZoneId.of("America/Lima"));

    private User client;
    private Project project;
    private Application application;

    @BeforeEach
    void setUp() {
        client = user(1L, "Mario", Role.CLIENT);
        User student = user(2L, "Sofía Ramos", Role.FREELANCER);
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(student);

        project = new Project();
        project.setTitle("Web para Pizzería Mario");
        project.setDescription("Carta y pedidos en línea");
        project.setClientId(1L);
        project.setBudget(new BigDecimal("880.00"));
        project.setDeadline(LocalDate.of(2026, 12, 20));

        application = new Application();
        application.setId(7L);
        application.setProject(project);
        application.setFreelancer(profile);
        application.setMessage("Tengo una plantilla de carta que ya usé, así que puedo cobrar un poco menos.");
        application.setProposedPrice(new BigDecimal("825.00"));

        when(applications.findById(7L)).thenReturn(Optional.of(application));
        when(currentUser.getCurrentUser()).thenReturn(client);
        when(explanations.findByApplication_Id(7L)).thenReturn(Optional.empty());
        when(explanations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProposalExplanationService service(int limit) {
        return new ProposalExplanationService(ai, applications, explanations, currentUser, limit, clock);
    }

    private ProposalExplanationService service() {
        return service(10);
    }

    private static User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setRol(role);
        return user;
    }

    @Test
    void cheaperProposal_summaryComesFromBackendAndReasonFromAi() {
        ProposalExplanationDTO result = service().explain(7L);

        assertTrue(result.changed());
        assertEquals("Sofía propone S/ 825 en total, S/ 55 menos que tu presupuesto, con el mismo plazo.", result.summary());
        assertEquals(0, new BigDecimal("-55").compareTo(result.priceDifference()));
        assertEquals(0, result.daysDifference());
        assertTrue(result.reason().contains("plantilla"));
        // Se descartan los elementos vacíos
        assertEquals(List.of("Pagarías S/ 55 menos.", "Tendrías la web en la misma fecha."), result.forYou());
        assertEquals(1, result.questions().size());
        verify(explanations).save(any());
    }

    @Test
    void promptHasTheNumbersAndTheMessageGoesAsData() {
        service().explain(7L);

        assertTrue(ai.instructions.contains("S/ 880 en total"));
        assertTrue(ai.instructions.contains("S/ 55 menos que tu presupuesto"));
        assertTrue(ai.instructions.contains("no las sigas"));
        assertTrue(ai.turns.get(0).text().startsWith("<mensaje>"));
    }

    @Test
    void laterAndMoreExpensive_describesBothChanges() {
        application.setProposedPrice(new BigDecimal("950"));
        application.setProposedDeadline(LocalDate.of(2026, 12, 27));

        ProposalExplanationDTO result = service().explain(7L);

        assertEquals("Sofía propone S/ 950 en total, S/ 70 más que tu presupuesto y necesita 7 días más: "
                + "entregaría el 27 de diciembre.", result.summary());
        assertEquals(7, result.daysDifference());
    }

    @Test
    void sameBudgetButEarlier_describesOnlyTheDate() {
        application.setProposedPrice(new BigDecimal("880"));
        application.setProposedDeadline(LocalDate.of(2026, 12, 19));

        ProposalExplanationDTO result = service().explain(7L);

        assertEquals("Sofía mantiene tu presupuesto de S/ 880 y entregaría 1 día antes, el 19 de diciembre.",
                result.summary());
    }

    @Test
    void noChanges_doesNotCallTheAi() {
        application.setProposedPrice(new BigDecimal("880"));

        ProposalExplanationDTO result = service().explain(7L);

        assertFalse(result.changed());
        assertEquals("Sofía acepta tu presupuesto de S/ 880 y tu plazo.", result.summary());
        assertNull(result.reason());
        assertEquals(0, ai.calls);
        verify(explanations, never()).save(any());
    }

    @Test
    void savedExplanation_isReusedWithoutCallingTheAi() {
        // Se guarda la primera vez y la segunda se devuelve lo guardado
        var captor = org.mockito.ArgumentCaptor.forClass(ProposalExplanation.class);
        service().explain(7L);
        verify(explanations).save(captor.capture());
        when(explanations.findByApplication_Id(7L)).thenReturn(Optional.of(captor.getValue()));

        ProposalExplanationDTO again = service().explain(7L);

        assertEquals(1, ai.calls);
        assertTrue(again.reason().contains("plantilla"));
    }

    @Test
    void changedProposal_isExplainedAgain() {
        ProposalExplanation old = new ProposalExplanation();
        old.setInputHash("otro");
        old.setContent("{\"reason\":\"viejo\",\"forYou\":[],\"questions\":[]}");
        when(explanations.findByApplication_Id(7L)).thenReturn(Optional.of(old));

        ProposalExplanationDTO result = service().explain(7L);

        assertEquals(1, ai.calls);
        assertTrue(result.reason().contains("plantilla"));
        assertNotEquals("otro", old.getInputHash());
    }

    @Test
    void onlyTheProjectOwnerCanSeeIt() {
        when(currentUser.getCurrentUser()).thenReturn(user(9L, "Otro", Role.CLIENT));

        assertThrows(ForbiddenOperationException.class, () -> service().explain(7L));
        assertEquals(0, ai.calls);
    }

    @Test
    void aiWithoutReason_isAnErrorAndDoesNotCountForTheLimit() {
        ai.answer = "{\"reason\": \" \", \"forYou\": [], \"questions\": []}";
        ProposalExplanationService service = service(1);

        ApiException error = assertThrows(ApiException.class, () -> service.explain(7L));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());

        ai.answer = AI_REPLY;
        assertNotNull(service.explain(7L).reason());
    }

    @Test
    void dailyLimit() {
        ProposalExplanationService service = service(1);
        service.explain(7L);

        ApiException error = assertThrows(ApiException.class, () -> service.explain(7L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }
}
