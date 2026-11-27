package pe.edu.utec.devutec.guide;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Payment;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectFeature;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.pricing.PriceEstimateDTO;
import pe.edu.utec.devutec.pricing.PriceEstimator;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StageGuideServiceTest {

    private static final String AI_REPLY = """
            ```json
            {"tasks": [{"title": "Armar la maqueta en Figma", "hours": 6},
                       {"title": "Elegir colores con el logo", "hours": 2},
                       {"title": "  ", "hours": 3}],
             "clientQuestions": ["¿Qué pizzas quieres destacar?", ""],
             "risks": ["Pide las fotos de tus platos antes de diseñar"],
             "tip": "Antes de programar, confirma el diseño con el cliente."}
            ```""";

    // IA falsa como Gemini: no soporta salidas estructuradas, así que se usa reply y se lee el JSON
    static class FakeAi implements AiGateway {
        String instructions;
        int calls;

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            this.instructions = instructions;
            calls++;
            return AI_REPLY;
        }
    }

    private final FakeAi ai = new FakeAi();
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final StageGuideRepository guides = mock(StageGuideRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PriceEstimator estimator = mock(PriceEstimator.class);
    // Hoy: 26 de noviembre de 2026
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T15:00:00Z"), ZoneId.of("America/Lima"));

    private User student;
    private Contract contract;
    private ContractStage design;

    @BeforeEach
    void setUp() {
        User client = user(1L, "Mario", Role.CLIENT);
        student = user(2L, "Sofía", Role.FREELANCER);
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(student);

        Project project = new Project();
        project.setTitle("Web para Pizzería Mario");
        project.setDescription("Carta y pedidos en línea");
        project.setCategory("WEB");
        project.setClientId(1L);
        project.setFeatures(List.of(new ProjectFeature("CARTA_CATALOGO", 1)));

        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);

        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("800"));

        contract = new Contract();
        contract.setId(50L);
        contract.setApplication(application);
        contract.setPayment(payment);
        contract.setStartedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        contract.addStage(stage(1L, 1, "Descubrimiento", StageStatus.APPROVED, LocalDate.of(2026, 11, 22)));
        design = stage(2L, 2, "Diseño", StageStatus.IN_PROGRESS, LocalDate.of(2026, 12, 2));
        contract.addStage(design);

        when(contracts.findById(50L)).thenReturn(Optional.of(contract));
        when(users.findById(1L)).thenReturn(Optional.of(client));
        when(currentUser.getCurrentUser()).thenReturn(student);
        when(guides.findByStage_Id(any())).thenReturn(Optional.empty());
        when(guides.save(any())).thenAnswer(invocation -> {
            StageGuide guide = invocation.getArgument(0);
            long id = 100;
            for (StageTask task : guide.getTasks()) task.setId(id++);
            return guide;
        });
        // Tabla de horas: 40 a 60 horas el proyecto; Diseño entre 12 y 18; promedio S/ 25 la hora
        when(estimator.estimate(any(), any())).thenReturn(new PriceEstimateDTO("HOURS", 0, "", List.of(
                new PriceEstimateDTO.Feature("CARTA_CATALOGO", "Carta o catálogo de productos", 1, 6, 10)),
                40, 60, new BigDecimal("25"), null, null, null, null, 4, 5,
                List.of(new PriceEstimateDTO.Stage("Diseño", 30, 12, 18))));
    }

    private StageGuideService service() {
        return new StageGuideService(ai, contracts, guides, users, currentUser, estimator, clock);
    }

    private static User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setRol(role);
        return user;
    }

    private static ContractStage stage(Long id, int position, String name, StageStatus status, LocalDate due) {
        ContractStage stage = new ContractStage();
        stage.setId(id);
        stage.setPosition(position);
        stage.setName(name);
        stage.setStatus(status);
        stage.setDueDate(due);
        return stage;
    }

    @Test
    void getOrCreate_armaLaGuiaConLasHorasDeLaTabla() {
        StageGuideResponseDTO guide = service().getOrCreate(50L, 2L);

        // La tarea vacía se descarta; 6 h y 2 h se ajustan al punto medio de Diseño (15 h)
        assertEquals(List.of("Armar la maqueta en Figma", "Elegir colores con el logo"),
                guide.tasks().stream().map(StageGuideResponseDTO.Task::title).toList());
        assertEquals(List.of(11, 4), guide.tasks().stream().map(StageGuideResponseDTO.Task::hours).toList());
        assertEquals(List.of("¿Qué pizzas quieres destacar?"), guide.clientQuestions());
        assertEquals(List.of("Pide las fotos de tus platos antes de diseñar"), guide.risks());
        assertEquals("Antes de programar, confirma el diseño con el cliente.", guide.tip());
        // La IA recibe el proyecto, la etapa y sus horas de referencia
        assertTrue(ai.instructions.contains("Web para Pizzería Mario"));
        assertTrue(ai.instructions.contains("Etapa de esta guía: «Diseño» (etapa 2 de 2)"));
        assertTrue(ai.instructions.contains("entre 12 y 18"));
        assertTrue(ai.instructions.contains("Carta o catálogo de productos"));
        verify(guides).save(any());
    }

    @Test
    void getOrCreate_siYaExiste_noLlamaALaIa() {
        StageGuide saved = new StageGuide();
        saved.setStage(design);
        saved.setClientQuestions("[]");
        saved.setRisks("[]");
        when(guides.findByStage_Id(2L)).thenReturn(Optional.of(saved));

        service().getOrCreate(50L, 2L);

        assertEquals(0, ai.calls);
        verify(guides, never()).save(any());
    }

    @Test
    void getOrCreate_conCambiosPedidos_leDiceALaIaQueLosIncluya() {
        design.setStatus(StageStatus.CHANGES_REQUESTED);
        design.setChangeRequestNote("Que se vea más alegre");

        service().getOrCreate(50L, 2L);

        assertTrue(ai.instructions.contains("«Que se vea más alegre»"));
    }

    @Test
    void soloElEstudianteDelContratoPuedeVerLaGuia() {
        when(currentUser.getCurrentUser()).thenReturn(user(1L, "Mario", Role.CLIENT));

        assertThrows(ForbiddenOperationException.class, () -> service().getOrCreate(50L, 2L));
        assertEquals(0, ai.calls);
    }

    @Test
    void etapaCancelada_noArmaGuia() {
        design.setStatus(StageStatus.CANCELLED);

        ApiException error = assertThrows(ApiException.class, () -> service().getOrCreate(50L, 2L));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void updateTask_marcaLaTareaYActualizaElAvance() {
        StageGuide guide = new StageGuide();
        guide.setStage(design);
        guide.setClientQuestions("[]");
        guide.setRisks("[]");
        StageTask task = new StageTask();
        task.setId(7L);
        task.setPosition(1);
        task.setTitle("Armar la maqueta");
        task.setHours(9);
        guide.addTask(task);
        StageTask other = new StageTask();
        other.setId(8L);
        other.setPosition(2);
        other.setTitle("Elegir colores");
        other.setHours(6);
        guide.addTask(other);
        when(guides.findByStage_Id(2L)).thenReturn(Optional.of(guide));

        StageGuideResponseDTO response = service().updateTask(50L, 2L, 7L, true);

        assertTrue(task.isDone());
        assertNotNull(task.getDoneAt());
        assertEquals(9, response.progress().hoursDone());
        assertEquals(60, response.progress().percent());
        // Diseño va del 22/11 al 02/12: al 26/11 debería llevar 40 %, lleva 60 %
        assertEquals(40, response.progress().expectedPercent());
        assertEquals("Diseño: 60 % · vas a tiempo", response.progress().label());
    }

    @Test
    void progress_atrasado_cuandoLlevaMuchoMenosDeLoEsperado() {
        List<StageGuideResponseDTO.Task> tasks = List.of(
                new StageGuideResponseDTO.Task(1L, "A", 10, false),
                new StageGuideResponseDTO.Task(2L, "B", 5, false));

        StageGuideResponseDTO.Progress progress = StageGuideService.progress(contract, design, tasks, LocalDate.of(2026, 12, 1));

        assertEquals("BEHIND", progress.schedule());
        assertEquals("Diseño: 0 % · vas un poco atrasado", progress.label());
    }

    @Test
    void priceCheck_avisaSiCobraMuchoMenosQueElPromedio() {
        PriceEstimateDTO estimate = new PriceEstimateDTO("HOURS", 0, "", List.of(), 33, 53, new BigDecimal("25"),
                null, null, null, null, 3, 5, List.of());
        contract.getPayment().setAmount(new BigDecimal("400"));

        StageGuideResponseDTO.PriceCheck check = StageGuideService.priceCheck(contract, estimate);

        assertTrue(check.low());
        assertEquals(new BigDecimal("9"), check.hourlyRate());
        assertTrue(check.message().startsWith("Ojo: proyectos así toman entre 33 y 53 horas. Con S/ 400 estás cobrando unos S/ 9 la hora"));
    }

    @Test
    void priceCheck_sinFuncionalidades_noHayRevision() {
        assertNull(StageGuideService.priceCheck(contract, null));
    }

    @Test
    void scaleHours_sinReferencia_usaLasHorasDeLaIaEntre1y20() {
        assertEquals(List.of(1, 20, 5), StageGuideService.scaleHours(List.of(0, 50, 5), null));
    }

    @Test
    void referenceHours_contratoDeUnaEtapa_usaElTotalDelProyecto() {
        ContractStage single = stage(9L, 1, "Trabajo completo", StageStatus.IN_PROGRESS, null);
        PriceEstimateDTO estimate = new PriceEstimateDTO("HOURS", 0, "", List.of(), 33, 53, BigDecimal.TEN,
                null, null, null, null, 3, 5, List.of());

        assertArrayEquals(new int[]{33, 53}, StageGuideService.referenceHours(single, 1, estimate));
    }
}
