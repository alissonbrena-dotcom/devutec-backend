package pe.edu.utec.devutec.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStage;
import pe.edu.utec.devutec.model.contract.StageStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class QualityServiceTest {

    private static final String AI_REPLY = """
            {"tasks": ["Comprime las fotos de la carta a WebP de menos de 200 KB", " ", "Agrega texto alternativo a las fotos"],
             "forClient": "Sofía está ajustando tu web para que cargue más rápido en celulares."}
            """;
    private static final QualityService.Minimums MIN = new QualityService.Minimums(50, 80, 80, 80);

    static class FakeAi implements AiGateway {
        int calls;
        String instructions;
        String answer = AI_REPLY;

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            this.instructions = instructions;
            calls++;
            if (answer == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "sin IA");
            return answer;
        }
    }

    static class FakePageSpeed implements PageSpeedClient {
        PageSpeedReport report;
        RuntimeException error;
        String url;

        @Override
        public PageSpeedReport analyze(String url) {
            this.url = url;
            if (error != null) throw error;
            return report;
        }
    }

    private final FakeAi ai = new FakeAi();
    private final FakePageSpeed pageSpeed = new FakePageSpeed();
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final QualityCheckRepository checks = mock(QualityCheckRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-28T15:00:00Z"), ZoneId.of("America/Lima"));
    private final List<QualityCheck> saved = new ArrayList<>();

    private User student;
    private User client;
    private Project project;
    private Contract contract;
    private ContractStage design;
    private ContractStage finalStage;

    @BeforeEach
    void setUp() {
        client = user(1L, "Mario", Role.CLIENT);
        student = user(2L, "Sofía", Role.FREELANCER);
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(student);
        project = new Project();
        project.setTitle("Web para Pizzería Mario");
        project.setClientId(1L);
        project.setCategory("WEB");
        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);
        contract = new Contract();
        contract.setId(50L);
        contract.setApplication(application);
        design = stage(1L, 1, "Diseño", StageStatus.APPROVED);
        finalStage = stage(2L, 2, "Entrega final", StageStatus.IN_PROGRESS);
        finalStage.setDueDate(LocalDate.of(2026, 12, 20));
        contract.addStage(design);
        contract.addStage(finalStage);

        when(contracts.findById(50L)).thenReturn(Optional.of(contract));
        when(currentUser.getCurrentUser()).thenReturn(student);
        when(checks.save(any())).thenAnswer(invocation -> {
            QualityCheck check = invocation.getArgument(0);
            check.setId((long) saved.size() + 1);
            saved.add(check);
            return check;
        });
        when(checks.findFirstByStage_IdOrderByCreatedAtDesc(anyLong()))
                .thenAnswer(invocation -> saved.isEmpty() ? Optional.empty() : Optional.of(saved.getLast()));
        pageSpeed.report = report(92, 95, 96, 100, true);
    }

    private QualityService service(int limit) {
        return new QualityService(ai, contracts, checks, currentUser, pageSpeed, MIN, true, limit, clock);
    }

    private QualityService service() {
        return service(15);
    }

    private static PageSpeedReport report(int performance, int accessibility, int bestPractices, int seo, boolean https) {
        return new PageSpeedReport(performance, accessibility, bestPractices, seo, https, List.of(
                new PageSpeedReport.Issue("Rendimiento", "Mejora la entrega de imágenes", "Ahorro estimado de 1.8 MB"),
                new PageSpeedReport.Issue("Accesibilidad", "Las imágenes no tienen atributos alt", "")));
    }

    private static User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setRol(role);
        return user;
    }

    private static ContractStage stage(Long id, int position, String name, StageStatus status) {
        ContractStage stage = new ContractStage();
        stage.setId(id);
        stage.setPosition(position);
        stage.setName(name);
        stage.setStatus(status);
        return stage;
    }

    @Test
    void goodWeb_passesWithoutCallingTheAi() {
        QualityCheckResponseDTO result = service().check(50L, 2L, "mi-pizzeria.vercel.app");

        assertEquals(QualityStatus.PASSED, result.status());
        assertEquals("https://mi-pizzeria.vercel.app", pageSpeed.url);
        assertTrue(result.failures().isEmpty());
        assertEquals(4, result.scores().size());
        assertTrue(result.scores().stream().allMatch(QualityCheckResponseDTO.Score::passed));
        assertEquals(0, ai.calls);
    }

    @Test
    void slowWeb_failsAndTheAiTurnsItIntoTasks() {
        pageSpeed.report = report(42, 70, 96, 100, true);

        QualityCheckResponseDTO result = service().check(50L, 2L, "https://mi-pizzeria.vercel.app");

        assertEquals(QualityStatus.FAILED, result.status());
        assertEquals(2, result.failures().size());
        assertTrue(result.failures().get(0).startsWith("Rendimiento: 42 de 100 (mínimo 50)"));
        assertEquals(List.of("Comprime las fotos de la carta a WebP de menos de 200 KB",
                "Agrega texto alternativo a las fotos"), result.tasks());
        assertNotNull(result.forClient());
        assertTrue(ai.instructions.contains("Mejora la entrega de imágenes"));
        assertTrue(ai.instructions.contains("20 de diciembre"));
    }

    @Test
    void withoutHttps_failsForSecurity() {
        pageSpeed.report = report(92, 95, 96, 100, false);

        QualityCheckResponseDTO result = service().check(50L, 2L, "http://mi-pizzeria.com");

        assertEquals(QualityStatus.FAILED, result.status());
        assertTrue(result.failures().get(0).startsWith("Seguridad"));
    }

    @Test
    void aiDown_stillShowsWhatToFixFromGoogle() {
        pageSpeed.report = report(42, 95, 96, 100, true);
        ai.answer = null;

        QualityCheckResponseDTO result = service().check(50L, 2L, "https://mi-pizzeria.vercel.app");

        assertEquals(QualityStatus.FAILED, result.status());
        assertEquals("Mejora la entrega de imágenes (Ahorro estimado de 1.8 MB)", result.tasks().get(0));
    }

    @Test
    void siteThatDoesNotOpen_fails() {
        pageSpeed.error = new PageSpeedClient.SiteNotReachableException("FAILED_DOCUMENT_REQUEST");

        QualityCheckResponseDTO result = service().check(50L, 2L, "https://no-existe.vercel.app");

        assertEquals(QualityStatus.FAILED, result.status());
        assertTrue(result.failures().get(0).startsWith("No pudimos abrir tu web"));
    }

    @Test
    void googleDown_isUnavailableAndDoesNotBlockTheDelivery() {
        pageSpeed.error = new PageSpeedClient.PageSpeedUnavailableException("timeout");
        QualityService service = service(1);

        assertEquals(QualityStatus.UNAVAILABLE, service.check(50L, 2L, "https://mi-pizzeria.vercel.app").status());
        assertDoesNotThrow(() -> service.ensurePassed(contract, finalStage));
        // No contó para el límite del día
        pageSpeed.error = null;
        assertEquals(QualityStatus.PASSED, service.check(50L, 2L, "https://mi-pizzeria.vercel.app").status());
    }

    @Test
    void finalDelivery_isBlockedWithoutACheck() {
        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> service().ensurePassed(contract, finalStage));
        assertTrue(error.getMessage().contains("Revisar mi web"));
    }

    @Test
    void finalDelivery_isBlockedUntilTheWebPasses() {
        QualityService service = service();
        pageSpeed.report = report(42, 95, 96, 100, true);
        service.check(50L, 2L, "https://mi-pizzeria.vercel.app");

        InvalidOperationException error = assertThrows(InvalidOperationException.class,
                () -> service.ensurePassed(contract, finalStage));
        assertTrue(error.getMessage().contains("antes del 20 de diciembre"));

        pageSpeed.report = report(75, 95, 96, 100, true);
        service.check(50L, 2L, "https://mi-pizzeria.vercel.app");
        assertDoesNotThrow(() -> service.ensurePassed(contract, finalStage));
    }

    @Test
    void onlyTheFinalStageOfWebProjectsIsBlocked() {
        ContractStage development = stage(3L, 1, "Desarrollo", StageStatus.IN_PROGRESS);
        Contract other = new Contract();
        other.setApplication(contract.getApplication());
        other.addStage(development);
        other.addStage(stage(4L, 2, "Entrega final", StageStatus.PENDING));
        assertDoesNotThrow(() -> service().ensurePassed(other, development));

        project.setCategory("AUTOMATIZACION");
        assertDoesNotThrow(() -> service().ensurePassed(contract, finalStage));
        assertThrows(InvalidOperationException.class, () -> service().check(50L, 2L, "https://mi-bot.com"));
    }

    @Test
    void disabled_doesNotBlock() {
        QualityService off = new QualityService(ai, contracts, checks, currentUser, pageSpeed, MIN, false, 15, clock);
        assertDoesNotThrow(() -> off.ensurePassed(contract, finalStage));
    }

    @Test
    void onlyTheStudentCanCheck_butTheClientSeesTheResult() {
        service().check(50L, 2L, "https://mi-pizzeria.vercel.app");
        when(currentUser.getCurrentUser()).thenReturn(client);

        assertThrows(ForbiddenOperationException.class,
                () -> service().check(50L, 2L, "https://mi-pizzeria.vercel.app"));
        QualityOverviewDTO overview = service().overview(50L, 2L);
        assertTrue(overview.required());
        assertEquals(QualityStatus.PASSED, overview.last().status());

        when(currentUser.getCurrentUser()).thenReturn(user(9L, "Otro", Role.CLIENT));
        assertThrows(ForbiddenOperationException.class, () -> service().overview(50L, 2L));
    }

    @Test
    void approvedStage_cannotBeChecked() {
        assertThrows(InvalidOperationException.class,
                () -> service().check(50L, 1L, "https://mi-pizzeria.vercel.app"));
    }

    @Test
    void dailyLimit() {
        QualityService service = service(1);
        service.check(50L, 2L, "https://mi-pizzeria.vercel.app");

        ApiException error = assertThrows(ApiException.class,
                () -> service.check(50L, 2L, "https://mi-pizzeria.vercel.app"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }

    @Test
    void parsesThePageSpeedReport() {
        String json = """
                {"lighthouseResult": {
                  "finalDisplayedUrl": "https://mi-pizzeria.vercel.app/",
                  "categories": {
                    "performance": {"score": 0.42, "auditRefs": [
                      {"id": "largest-contentful-paint", "weight": 25},
                      {"id": "uses-optimized-images", "weight": 0},
                      {"id": "render-blocking-resources", "weight": 0}]},
                    "accessibility": {"score": 0.7, "auditRefs": [
                      {"id": "image-alt", "weight": 10}, {"id": "color-contrast", "weight": 7}]},
                    "best-practices": {"score": 0.96, "auditRefs": []},
                    "seo": {"score": 1, "auditRefs": []}
                  },
                  "audits": {
                    "largest-contentful-paint": {"title": "Procesamiento de imagen con contenido más grande", "score": 0.1, "scoreDisplayMode": "numeric", "displayValue": "6.2 s"},
                    "uses-optimized-images": {"title": "Codifica las imágenes de forma eficaz", "score": 0.3, "scoreDisplayMode": "metricSavings", "displayValue": "Ahorro estimado de 1.8 MB"},
                    "render-blocking-resources": {"title": "Elimina los recursos que bloquean el renderizado", "score": 0.8, "scoreDisplayMode": "metricSavings"},
                    "image-alt": {"title": "Las imágenes no tienen atributos alt", "score": 0, "scoreDisplayMode": "binary"},
                    "color-contrast": {"title": "Contraste suficiente", "score": 1, "scoreDisplayMode": "binary"},
                    "is-on-https": {"score": 1, "scoreDisplayMode": "binary"}
                  }
                }}
                """;

        PageSpeedReport report = PageSpeedRestClient.parse(JsonMapper.builder().build().readTree(json));

        assertEquals(42, report.performance());
        assertEquals(70, report.accessibility());
        assertEquals(96, report.bestPractices());
        assertEquals(100, report.seo());
        assertTrue(report.https());
        // Rendimiento: la métrica con peso y la oportunidad importante (score < 0.5), no la menor
        assertEquals(List.of("Procesamiento de imagen con contenido más grande", "Codifica las imágenes de forma eficaz",
                "Las imágenes no tienen atributos alt"), report.issues().stream().map(PageSpeedReport.Issue::title).toList());
        assertEquals("6.2 s", report.issues().get(0).detail());
    }
}
