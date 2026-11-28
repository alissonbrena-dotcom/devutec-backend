package pe.edu.utec.devutec.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MetricsServiceTest {

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final TrackedSiteRepository sites = mock(TrackedSiteRepository.class);
    private final MetricEventRepository events = mock(MetricEventRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    // Hoy: 28 de noviembre de 2026, 10:00 en Lima
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-28T15:00:00Z"), ZoneId.of("America/Lima"));
    private final List<MetricEvent> saved = new ArrayList<>();

    private Project project;
    private Contract contract;
    private TrackedSite site;
    private User client;

    @BeforeEach
    void setUp() {
        client = user(1L, Role.CLIENT);
        User student = user(2L, Role.FREELANCER);
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(student);
        project = new Project();
        project.setClientId(1L);
        project.setCategory("WEB");
        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);
        contract = new Contract();
        contract.setId(50L);
        contract.setApplication(application);

        site = new TrackedSite();
        site.setId(9L);
        site.setContract(contract);
        site.setSiteKey("dvt_abc");

        when(contracts.findById(50L)).thenReturn(Optional.of(contract));
        when(currentUser.getCurrentUser()).thenReturn(student);
        when(sites.findBySiteKey("dvt_abc")).thenReturn(Optional.of(site));
        when(sites.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    private MetricsService service(int perMinute) {
        return new MetricsService(contracts, sites, events, currentUser, "https://devutec.duckdns.org/api/v1/",
                perMinute, clock);
    }

    private MetricsService service() {
        return service(120);
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRol(role);
        return user;
    }

    private static MetricEvent event(String type, String label, String session, Integer loadMs, String path,
                                     LocalDateTime at) {
        MetricEvent event = new MetricEvent();
        event.setType(type);
        event.setLabel(label);
        event.setSessionId(session);
        event.setLoadMs(loadMs);
        event.setPath(path);
        event.setCreatedAt(at);
        return event;
    }

    @Test
    void recordsAVisitWithItsLoadTime() {
        assertTrue(service().record("{\"k\":\"dvt_abc\",\"t\":\"view\",\"p\":\"/carta\",\"s\":\"x1\",\"ms\":1450}"));

        MetricEvent event = saved.getFirst();
        assertEquals(MetricEvent.VIEW, event.getType());
        assertEquals("/carta", event.getPath());
        assertEquals(1450, event.getLoadMs());
        assertEquals(site, event.getSite());
    }

    @Test
    void recordsAnOrderInLowercase() {
        assertTrue(service().record("{\"k\":\"dvt_abc\",\"t\":\"event\",\"l\":\"Pedido\",\"s\":\"x1\"}"));
        assertEquals("pedido", saved.getFirst().getLabel());
    }

    @Test
    void ignoresGarbageUnknownSitesAndEventsWithoutName() {
        MetricsService service = service();
        assertFalse(service.record("no es json"));
        assertFalse(service.record(null));
        assertFalse(service.record("{\"k\":\"otro\",\"t\":\"view\"}"));
        assertFalse(service.record("{\"k\":\"dvt_abc\",\"t\":\"hack\"}"));
        assertFalse(service.record("{\"k\":\"dvt_abc\",\"t\":\"event\"}"));
        assertTrue(saved.isEmpty());
    }

    @Test
    void absurdLoadTimesAreIgnoredAndLongTextsAreCut() {
        service().record("{\"k\":\"dvt_abc\",\"t\":\"view\",\"p\":\"/" + "a".repeat(500) + "\",\"ms\":999999}");

        assertNull(saved.getFirst().getLoadMs());
        assertEquals(200, saved.getFirst().getPath().length());
    }

    @Test
    void perMinuteLimitStopsSpam() {
        MetricsService service = service(2);
        String body = "{\"k\":\"dvt_abc\",\"t\":\"view\"}";
        assertTrue(service.record(body));
        assertTrue(service.record(body));
        assertFalse(service.record(body));
    }

    @Test
    void setup_createsTheSiteKeyAndTheSnippet() {
        when(sites.findByContract_Id(50L)).thenReturn(Optional.empty());
        when(events.findFirstBySite_IdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        MetricsSetupDTO setup = service().setup(50L);

        assertTrue(setup.webProject());
        assertTrue(setup.siteKey().matches("dvt_[a-z2-9]{20}"));
        assertEquals("https://devutec.duckdns.org/api/v1/metrics/sdk.js", setup.scriptUrl());
        assertTrue(setup.snippet().contains("data-site=\"" + setup.siteKey() + "\""));
        assertFalse(setup.installed());
        ArgumentCaptor<TrackedSite> captor = ArgumentCaptor.forClass(TrackedSite.class);
        verify(sites).save(captor.capture());
        assertEquals(contract, captor.getValue().getContract());
    }

    @Test
    void setup_onlyForWebProjects() {
        project.setCategory("APP");

        MetricsSetupDTO setup = service().setup(50L);

        assertFalse(setup.webProject());
        assertNull(setup.snippet());
        verify(sites, never()).save(any());
    }

    @Test
    void onlyTheParticipantsCanSeeIt() {
        when(currentUser.getCurrentUser()).thenReturn(user(7L, Role.CLIENT));

        assertThrows(ForbiddenOperationException.class, () -> service().setup(50L));
        assertThrows(ForbiddenOperationException.class, () -> service().dashboard(50L, 30));
    }

    @Test
    void dashboard_withoutSite_saysItIsNotInstalled() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        when(sites.findByContract_Id(anyLong())).thenReturn(Optional.empty());

        MetricsDashboardDTO dashboard = service().dashboard(50L, 7);

        assertFalse(dashboard.installed());
        assertEquals(7, dashboard.daily().size());
        assertTrue(dashboard.summary().startsWith("Todavía no hay datos"));
    }

    @Test
    void summarize_countsVisitsVisitorsOrdersAndTheDailySeries() {
        LocalDate first = LocalDate.of(2026, 11, 26);
        LocalDateTime day1 = first.atTime(12, 0);
        LocalDateTime day3 = first.plusDays(2).atTime(9, 0);
        List<MetricEvent> list = List.of(
                event("view", null, "a", 1000, "/", day1),
                event("view", null, "a", 3000, "/carta", day1),
                event("view", null, "b", null, "/carta", day3),
                event("event", "pedido", "a", null, "/carta", day3),
                event("event", "whatsapp", "b", null, "/", day3));

        MetricsDashboardDTO dashboard = MetricsService.summarize(list, 3, first, day3);

        assertEquals(3, dashboard.visits());
        assertEquals(2, dashboard.visitors());
        assertEquals(1, dashboard.orders());
        assertEquals(2000, dashboard.averageLoadMs());
        assertEquals(List.of(2L, 0L, 1L), dashboard.daily().stream().map(MetricsDashboardDTO.Day::visits).toList());
        assertEquals(1, dashboard.daily().get(2).orders());
        assertEquals("/carta", dashboard.topPages().getFirst().name());
        assertEquals(2, dashboard.topEvents().size());
        assertEquals("Tu web tuvo 3 visitas de 2 personas y 1 pedido en los últimos 3 días.", dashboard.summary());
    }

    @Test
    void summary_withoutVisits() {
        assertEquals("Todavía no hay visitas en los últimos 30 días.", MetricsService.summary(0, 0, 0, 30));
    }

    @Test
    void siteKeysAreRandom() {
        assertNotEquals(MetricsService.newKey(), MetricsService.newKey());
    }
}
