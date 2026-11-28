package pe.edu.utec.devutec.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    // 28 de noviembre de 2026, 10:00 a. m. en Lima
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0);

    @Mock
    private MetricsSiteRepository siteRepository;
    @Mock
    private MetricDailyCountRepository countRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AmplitudeClient amplitude;
    @Mock
    private MetricsStripeGateway stripe;

    private MetricsService service;
    private Contract contract;
    private Project project;
    private User client;
    private User freelancer;

    @BeforeEach
    void setUp() {
        service = new MetricsService(siteRepository, countRepository, contractRepository, currentUserService, amplitude, stripe,
                "https://devutec.vercel.app", "https://devutec.vercel.app/metricas.js", "https://devutec.duckdns.org/api/v1/collect");
        service.setClock(Clock.fixed(NOW.atZone(LIMA).toInstant(), LIMA));
        client = user(1L);
        freelancer = user(2L);
        project = new Project();
        project.setClientId(1L);
        project.setTitle("Web para Pizzería Ramón");
        project.setCategory("WEB");
        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(freelancer);
        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);
        contract = new Contract();
        contract.setId(5L);
        contract.setApplication(application);
        contract.setStatus(ContractStatus.IN_PROGRESS);
        lenient().when(contractRepository.findById(5L)).thenReturn(Optional.of(contract));
        lenient().when(siteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- Activar ----------

    @Test
    void enable_elCliente_creaLaLlaveConSuConsentimientoYLasInstrucciones() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.empty());

        MetricsResponseDTO response = service.enable(5L);

        ArgumentCaptor<MetricsSite> saved = ArgumentCaptor.forClass(MetricsSite.class);
        verify(siteRepository).save(saved.capture());
        assertTrue(saved.getValue().getPublicKey().startsWith("dv_"));
        assertEquals(1L, saved.getValue().getConsentedByUserId());
        assertEquals(MetricsService.CONSENT_VERSION, saved.getValue().getConsentVersion());
        assertTrue(response.enabled());
        assertFalse(response.integrated());
        assertTrue(response.integration().scriptTag().contains(saved.getValue().getPublicKey()));
        assertTrue(response.integration().aiPrompt().contains("data-devutec=\"contacto\""));
        // La prueba gratis todavía no empieza: empieza con el primer dato real
        assertNull(response.access().trialEndsAt());
        assertFalse(response.access().locked());
    }

    @Test
    void enable_elFreelancer_noPuede() {
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);

        assertThrows(ForbiddenOperationException.class, () -> service.enable(5L));
        verify(siteRepository, never()).save(any());
    }

    @Test
    void get_sinActivar_muestraElTextoLegalYSiElClienteLasPidio() {
        project.setMetricsRequested(true);
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.empty());

        MetricsResponseDTO response = service.get(5L);

        assertFalse(response.enabled());
        assertTrue(response.requested());
        assertFalse(response.canManage());
        assertNull(response.integration());
        assertTrue(response.consentText().contains("Ley 29733"));
        assertTrue(response.consentText().contains("Amplitude"));
    }

    @Test
    void get_alguienDeAfuera_noPuedeVerlas() {
        when(currentUserService.getCurrentUser()).thenReturn(user(99L));

        assertThrows(ForbiddenOperationException.class, () -> service.get(5L));
    }

    // ---------- Recibir datos ----------

    @Test
    void collect_primerDatoReal_fijaElDominioEmpiezaLaPruebaYSeReenviaAAmplitudSinDatosPersonales() {
        MetricsSite site = site();
        when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site));
        when(countRepository.findBySiteIdAndDayAndTypeAndTest(9L, NOW.toLocalDate(), MetricType.CONTACT, false))
                .thenReturn(Optional.empty());

        boolean saved = service.collect(new CollectRequestDTO("dv_abc", "contact", "/contacto?nombre=Ana", null, null,
                "visita12345", "instagram", "celular"), "https://PizzeriaRamon.pe", "1.2.3.4");

        assertTrue(saved);
        assertEquals("https://pizzeriaramon.pe", site.getAllowedOrigin());
        assertEquals(NOW, site.getFirstEventAt());
        assertEquals(NOW.plusDays(90), site.getTrialEndsAt());
        ArgumentCaptor<MetricDailyCount> count = ArgumentCaptor.forClass(MetricDailyCount.class);
        verify(countRepository).save(count.capture());
        assertEquals(1, count.getValue().getTotal());
        assertFalse(count.getValue().isTest());

        ArgumentCaptor<AmplitudeClient.Event> event = ArgumentCaptor.forClass(AmplitudeClient.Event.class);
        verify(amplitude).send(event.capture());
        assertEquals("devutec_contact", event.getValue().eventType());
        assertEquals("v_visita12345", event.getValue().deviceId());
        assertEquals("/contacto", event.getValue().properties().get("pagina"));
        assertEquals("dv_abc", event.getValue().properties().get("web"));
        assertEquals("instagram", event.getValue().properties().get("origen"));
        assertFalse(event.getValue().properties().containsValue("1.2.3.4"));
    }

    @Test
    void collect_desdeOtroDominio_seIgnora() {
        MetricsSite site = site();
        site.setAllowedOrigin("https://pizzeriaramon.pe");
        when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site));

        assertFalse(service.collect(new CollectRequestDTO("dv_abc", "PAGEVIEW", "/", null, null, null, null, null),
                "https://otra-web.com", "1.2.3.4"));
        verify(countRepository, never()).save(any());
        verify(amplitude, never()).send(any());
    }

    @Test
    void collect_desdeLocalhostOLaPaginaDePrueba_cuentaComoPruebaYNoEmpiezaLos3Meses() {
        MetricsSite site = site();
        site.setAllowedOrigin("https://pizzeriaramon.pe");
        when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site));

        assertTrue(service.collect(new CollectRequestDTO("dv_abc", "PAGEVIEW", "/", null, null, null, null, null),
                "http://localhost:5173", null));
        assertTrue(service.collect(new CollectRequestDTO("dv_abc", "PAGEVIEW", "/", null, null, null, null, null),
                "https://devutec.vercel.app", null));
        assertTrue(service.collect(new CollectRequestDTO("dv_abc", "PAGEVIEW", "/", null, null, null, null, null),
                "https://pizzeria-git-main-sofia.vercel.app", null));

        assertNotNull(site.getFirstEventAt());
        assertNull(site.getTrialEndsAt());
        assertEquals("https://pizzeriaramon.pe", site.getAllowedOrigin());
    }

    @Test
    void collect_desdeElServidorDelEstudianteSinOrigin_seAcepta() {
        when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site()));

        assertTrue(service.collect(new CollectRequestDTO("dv_abc", "CUSTOM", "/", null, "Reporte Generado!", null, null, null),
                null, "5.6.7.8"));

        ArgumentCaptor<AmplitudeClient.Event> event = ArgumentCaptor.forClass(AmplitudeClient.Event.class);
        verify(amplitude).send(event.capture());
        assertEquals("devutec_custom", event.getValue().eventType());
        assertEquals("reporte_generado_", event.getValue().properties().get("nombre"));
    }

    @Test
    void collect_llaveDesconocidaTipoInvalidoOVelocidadImposible_seIgnora() {
        lenient().when(siteRepository.findByPublicKey("dv_nada")).thenReturn(Optional.empty());
        lenient().when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site()));

        assertFalse(service.collect(new CollectRequestDTO("dv_nada", "PAGEVIEW", "/", null, null, null, null, null), null, null));
        assertFalse(service.collect(new CollectRequestDTO("dv_abc", "HACKEO", "/", null, null, null, null, null), null, null));
        assertFalse(service.collect(new CollectRequestDTO("dv_abc", "PERFORMANCE", "/", 999_999, null, null, null, null), null, null));
        assertFalse(service.collect(new CollectRequestDTO("dv_abc", "CUSTOM", "/", null, "   ", null, null, null), null, null));
        assertFalse(service.collect(null, null, null));
        verify(amplitude, never()).send(any());
    }

    @Test
    void collect_demasiadosEventosEnUnMinutoDesdeLaMismaIp_dejaDeContar() {
        when(siteRepository.findByPublicKey("dv_abc")).thenReturn(Optional.of(site()));
        for (int i = 0; i < MetricsService.MAX_EVENTS_PER_MINUTE; i++) {
            assertTrue(service.collect(new CollectRequestDTO("dv_abc", "CLICK", "/", null, null, null, null, null), null, "1.1.1.1"));
        }

        assertFalse(service.collect(new CollectRequestDTO("dv_abc", "CLICK", "/", null, null, null, null, null), null, "1.1.1.1"));
        assertTrue(service.collect(new CollectRequestDTO("dv_abc", "CLICK", "/", null, null, null, null, null), null, "2.2.2.2"));
    }

    // ---------- Panel básico ----------

    @Test
    void get_conDatos_sumaElUltimoMesDesdeNuestraBaseYPromediaLaVelocidad() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        site.setFirstEventAt(NOW.minusDays(3));
        site.setTrialEndsAt(NOW.plusDays(87));
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        LocalDate today = NOW.toLocalDate();
        when(countRepository.findBySiteIdAndDayGreaterThanEqual(eq(9L), any())).thenReturn(List.of(
                count(today, MetricType.PAGEVIEW, 30, 0, false),
                count(today.minusDays(1), MetricType.PAGEVIEW, 12, 0, true),
                count(today, MetricType.CONTACT, 4, 0, false),
                count(today, MetricType.ORDER, 2, 0, false),
                count(today, MetricType.PERFORMANCE, 2, 2500, false)));

        MetricsResponseDTO response = service.get(5L);

        assertEquals(42, response.summary().visits());
        assertEquals(4, response.summary().contacts());
        assertEquals(2, response.summary().orders());
        assertEquals(1250, response.summary().averageLoadMs());
        assertTrue(response.summary().includesTestData());
        assertEquals(14, response.summary().lastDays().size());
        assertEquals(30, response.summary().lastDays().get(13).visits());
        assertEquals(4, response.summary().lastDays().get(13).contacts());
        assertTrue(response.access().trialActive());
        assertFalse(response.access().locked());
    }

    @Test
    void get_terminoLaPruebaSinPlan_bloqueaLosNumeros() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        site.setFirstEventAt(NOW.minusDays(100));
        site.setTrialEndsAt(NOW.minusDays(10));
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));

        MetricsResponseDTO response = service.get(5L);

        assertTrue(response.access().locked());
        assertNull(response.summary());
        verify(countRepository, never()).findBySiteIdAndDayGreaterThanEqual(anyLong(), any());
    }

    @Test
    void disable_borraLaLlaveYLosContadores() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));

        MetricsResponseDTO response = service.disable(5L);

        verify(countRepository).deleteBySiteId(9L);
        verify(siteRepository).delete(site);
        assertFalse(response.enabled());
    }

    // ---------- Entrega final ----------

    @Test
    void checkFinalDelivery_pedidasYActivadasSinDatos_bloquea() {
        project.setMetricsRequested(true);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site()));

        InvalidOperationException error = assertThrows(InvalidOperationException.class, () -> service.checkFinalDelivery(contract));
        assertTrue(error.getMessage().startsWith("El cliente pidió métricas"));
    }

    @Test
    void checkFinalDelivery_conAlgunDato_dejaEntregar() {
        project.setMetricsRequested(true);
        MetricsSite site = site();
        site.setFirstEventAt(NOW);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));

        assertDoesNotThrow(() -> service.checkFinalDelivery(contract));
    }

    @Test
    void checkFinalDelivery_pedidasPeroNuncaActivadasONoPedidas_noBloquea() {
        project.setMetricsRequested(true);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.checkFinalDelivery(contract));

        project.setMetricsRequested(false);
        assertDoesNotThrow(() -> service.checkFinalDelivery(contract));
    }

    // ---------- Membresía ----------

    @Test
    void startSubscription_duranteLaPrueba_elPrimerCobroEsAlTerminarLos3Meses() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        site.setTrialEndsAt(NOW.plusDays(60));
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        when(stripe.createCheckout(eq(5L), eq(MetricsPlan.PRO), any(), any(), any()))
                .thenReturn(new MetricsStripeGateway.SubscriptionSession("cs_1", "https://checkout.stripe.com/x", "open", null, "5", "PRO"));

        MetricsSubscriptionDTOs.StartResponse response = service.startSubscription(5L, MetricsPlan.PRO);

        assertEquals("https://checkout.stripe.com/x", response.checkoutUrl());
        verify(stripe).createCheckout(eq(5L), eq(MetricsPlan.PRO), eq(NOW.plusDays(60).atZone(LIMA).toEpochSecond()), any(), any());
    }

    @Test
    void startSubscription_sinPruebaActiva_cobraDesdeHoy() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        site.setTrialEndsAt(NOW.minusDays(1));
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        when(stripe.createCheckout(eq(5L), eq(MetricsPlan.BASIC), isNull(), any(), any()))
                .thenReturn(new MetricsStripeGateway.SubscriptionSession("cs_2", "https://checkout.stripe.com/y", "open", null, "5", "BASIC"));

        assertEquals("cs_2", service.startSubscription(5L, MetricsPlan.BASIC).sessionId());
    }

    @Test
    void startSubscription_elFreelancer_noPuede() {
        when(currentUserService.getCurrentUser()).thenReturn(freelancer);

        assertThrows(ForbiddenOperationException.class, () -> service.startSubscription(5L, MetricsPlan.BASIC));
    }

    @Test
    void confirmSubscription_completa_guardaElPlan() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        when(stripe.getCheckout("cs_1"))
                .thenReturn(new MetricsStripeGateway.SubscriptionSession("cs_1", null, "complete", "sub_123", "5", "PRO"));

        MetricsResponseDTO response = service.confirmSubscription(5L, "cs_1");

        assertEquals(MetricsPlan.PRO, site.getPlan());
        assertEquals("sub_123", site.getStripeSubscriptionId());
        assertEquals(MetricsPlan.PRO, response.access().plan());
    }

    @Test
    void confirmSubscription_deOtroContratoOSinPagar_noCambiaElPlan() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        when(stripe.getCheckout("cs_x"))
                .thenReturn(new MetricsStripeGateway.SubscriptionSession("cs_x", null, "complete", "sub_9", "77", "PRO"));
        when(stripe.getCheckout("cs_open"))
                .thenReturn(new MetricsStripeGateway.SubscriptionSession("cs_open", null, "open", null, "5", "PRO"));

        assertThrows(InvalidOperationException.class, () -> service.confirmSubscription(5L, "cs_x"));
        assertThrows(InvalidOperationException.class, () -> service.confirmSubscription(5L, "cs_open"));
        assertEquals(MetricsPlan.NONE, site.getPlan());
    }

    // ---------- Reporte Pro ----------

    @Test
    void advanced_sinPlanPro_explicaQueEsDelPro() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site()));

        AdvancedReportDTO report = service.advanced(5L);

        assertFalse(report.available());
        verify(amplitude, never()).segmentation(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void advanced_conPro_leeAmplitudeYOrdenaDeMayorAMenor() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        MetricsSite site = site();
        site.setPlan(MetricsPlan.PRO);
        when(siteRepository.findByContractId(5L)).thenReturn(Optional.of(site));
        when(amplitude.canRead()).thenReturn(true);
        AmplitudeClient.Segmentation pages = new AmplitudeClient.Segmentation(List.of("/", "/carta"), List.of(10L, 25L),
                List.of("2026-11-27", "2026-11-28"), List.of(List.of(4L, 6L), List.of(10L, 15L)));
        AmplitudeClient.Segmentation empty = new AmplitudeClient.Segmentation(List.of(), List.of(), List.of(), List.of());
        when(amplitude.segmentation(eq("devutec_pageview"), eq("dv_abc"), any(), any(), eq("pagina"), eq(1))).thenReturn(pages);
        when(amplitude.segmentation(eq("devutec_pageview"), eq("dv_abc"), any(), any(), eq("origen"), eq(1))).thenReturn(empty);
        when(amplitude.segmentation(eq("devutec_pageview"), eq("dv_abc"), any(), any(), eq("dispositivo"), eq(1))).thenReturn(empty);
        when(amplitude.segmentation(eq("devutec_contact"), eq("dv_abc"), any(), any(), isNull(), eq(30))).thenReturn(empty);

        AdvancedReportDTO report = service.advanced(5L);

        assertTrue(report.available());
        assertEquals("/carta", report.topPages().get(0).label());
        assertEquals(25, report.topPages().get(0).total());
    }

    // ---------- Limpieza de datos ----------

    @Test
    void limpiezas_quitanLoQuePodriaSerPersonal() {
        assertEquals("/", MetricsService.cleanPath(null));
        assertEquals("/carta", MetricsService.cleanPath("carta?mesa=4"));
        assertEquals("/pedido", MetricsService.cleanPath("/pedido#paso2"));
        assertEquals(MetricsService.MAX_PATH, MetricsService.cleanPath("/" + "a".repeat(500)).length());
        assertEquals("https://pizzeria.pe", MetricsService.cleanOrigin("https://Pizzeria.pe:443"));
        assertEquals("http://localhost:5173", MetricsService.cleanOrigin("http://localhost:5173"));
        assertNull(MetricsService.cleanOrigin("null"));
        assertNull(MetricsService.cleanName("!!!"));
        assertEquals("ejecucion", MetricsService.cleanName("Ejecucion"));
    }

    @Test
    void amplitudeParse_leeEtiquetasConYSinAgrupar() {
        java.util.Map<String, Object> data = java.util.Map.of(
                "xValues", List.of("2026-11-27", "2026-11-28"),
                "seriesLabels", List.of(List.of(0, "/carta"), List.of(1, "/")),
                "series", List.of(List.of(3, 4), List.of(1, 0)));

        AmplitudeClient.Segmentation result = AmplitudeClient.parse(java.util.Map.of("data", data));

        assertEquals(List.of("/carta", "/"), result.labels());
        assertEquals(List.of(7L, 1L), result.totals());
        assertEquals(8, result.total());
    }

    private MetricsSite site() {
        MetricsSite site = new MetricsSite();
        site.setId(9L);
        site.setContractId(5L);
        site.setPublicKey("dv_abc");
        site.setConsentedByUserId(1L);
        site.setConsentedAt(NOW.minusDays(5));
        site.setConsentVersion(MetricsService.CONSENT_VERSION);
        return site;
    }

    private static MetricDailyCount count(LocalDate day, MetricType type, long total, long sumMs, boolean test) {
        MetricDailyCount count = new MetricDailyCount();
        count.setSiteId(9L);
        count.setDay(day);
        count.setType(type);
        count.setTotal(total);
        count.setSumValueMs(sumMs);
        count.setTest(test);
        return count;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

}
