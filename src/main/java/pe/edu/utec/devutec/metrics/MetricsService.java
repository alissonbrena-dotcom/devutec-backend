package pe.edu.utec.devutec.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// Capa de métricas de DEVUTEC (Fase 7 del plan): el estudiante pega un script en la web que entrega y el cliente
// ve en DEVUTEC cuántas visitas y pedidos tiene su web. Es anónimo: sin IP, sin cookies y sin datos personales.
@Slf4j
@Service
public class MetricsService {

    static final String WEB = "WEB";
    static final String ORDER = "pedido";
    static final int MAX_DAYS = 90;
    private static final String KEY_CHARS = "abcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ContractRepository contractRepository;
    private final TrackedSiteRepository siteRepository;
    private final MetricEventRepository eventRepository;
    private final CurrentUserService currentUserService;
    private final String publicApiUrl;
    private final int perMinuteLimit;
    private final Clock clock;
    // Eventos recibidos por sitio en el minuto actual: "siteKey:minuto" → cantidad (frena el spam)
    private final Map<String, Integer> perMinute = new ConcurrentHashMap<>();

    @Autowired
    public MetricsService(ContractRepository contractRepository, TrackedSiteRepository siteRepository,
                          MetricEventRepository eventRepository, CurrentUserService currentUserService,
                          @Value("${metrics.public-api-url:http://localhost:8080/api/v1}") String publicApiUrl,
                          @Value("${metrics.events-per-minute:120}") int perMinuteLimit) {
        this(contractRepository, siteRepository, eventRepository, currentUserService, publicApiUrl, perMinuteLimit,
                Clock.systemDefaultZone());
    }

    MetricsService(ContractRepository contractRepository, TrackedSiteRepository siteRepository,
                   MetricEventRepository eventRepository, CurrentUserService currentUserService,
                   String publicApiUrl, int perMinuteLimit, Clock clock) {
        this.contractRepository = contractRepository;
        this.siteRepository = siteRepository;
        this.eventRepository = eventRepository;
        this.currentUserService = currentUserService;
        this.publicApiUrl = publicApiUrl.replaceAll("/+$", "");
        this.perMinuteLimit = perMinuteLimit;
        this.clock = clock;
    }

    // ---------- Recibir eventos (público, desde la web del cliente) ----------

    // Devuelve true si se guardó. Lo que no sirve (sitio desconocido, JSON roto, spam) se descarta en silencio:
    // la web del cliente nunca debe fallar por las métricas.
    @Transactional
    public boolean record(String body) {
        JsonNode root;
        try {
            root = JSON.readTree(body == null ? "" : body);
        } catch (JacksonException e) {
            return false;
        }
        if (root == null || !root.isObject()) return false;
        String key = text(root, "k", 32);
        String type = text(root, "t", 10);
        if (key == null || !(MetricEvent.VIEW.equals(type) || MetricEvent.EVENT.equals(type))) return false;

        TrackedSite site = siteRepository.findBySiteKey(key).orElse(null);
        if (site == null || !underLimit(key)) return false;

        MetricEvent event = new MetricEvent();
        event.setSite(site);
        event.setType(type);
        event.setPath(text(root, "p", 200));
        event.setSessionId(text(root, "s", 40));
        if (MetricEvent.EVENT.equals(type)) {
            String label = text(root, "l", 60);
            if (label == null) return false;
            event.setLabel(label.toLowerCase());
        } else {
            JsonNode ms = root.path("ms");
            // Más de 2 minutos no es una carga real: se ignora ese dato
            if (ms.isNumber() && ms.asInt() > 0 && ms.asInt() < 120_000) event.setLoadMs(ms.asInt());
        }
        event.setCreatedAt(LocalDateTime.now(clock));
        eventRepository.save(event);
        return true;
    }

    private boolean underLimit(String key) {
        String minute = key + ":" + LocalDateTime.now(clock).withSecond(0).withNano(0);
        if (perMinute.size() > 10_000) perMinute.clear();
        return perMinute.merge(minute, 1, Integer::sum) <= perMinuteLimit;
    }

    // ---------- «Cómo integrarlo» ----------

    @Transactional
    public MetricsSetupDTO setup(Long contractId) {
        Contract contract = findContractOfParticipant(contractId);
        if (!isWeb(contract)) {
            return new MetricsSetupDTO(false, null, null, null, null, false, null);
        }
        TrackedSite site = siteFor(contract);
        String scriptUrl = publicApiUrl + "/metrics/sdk.js";
        String snippet = "<script defer src=\"" + scriptUrl + "\" data-site=\"" + site.getSiteKey() + "\"></script>";
        String orderSnippet = "<button data-devutec-event=\"pedido\">Hacer pedido</button>";
        LocalDateTime last = eventRepository.findFirstBySite_IdOrderByCreatedAtDesc(site.getId())
                .map(MetricEvent::getCreatedAt).orElse(null);
        return new MetricsSetupDTO(true, site.getSiteKey(), scriptUrl, snippet, orderSnippet, last != null, last);
    }

    private TrackedSite siteFor(Contract contract) {
        return siteRepository.findByContract_Id(contract.getId()).orElseGet(() -> {
            TrackedSite site = new TrackedSite();
            site.setContract(contract);
            site.setSiteKey(newKey());
            return siteRepository.save(site);
        });
    }

    static String newKey() {
        StringBuilder key = new StringBuilder("dvt_");
        for (int i = 0; i < 20; i++) key.append(KEY_CHARS.charAt(RANDOM.nextInt(KEY_CHARS.length())));
        return key.toString();
    }

    // ---------- Panel ----------

    @Transactional(readOnly = true)
    public MetricsDashboardDTO dashboard(Long contractId, int requestedDays) {
        Contract contract = findContractOfParticipant(contractId);
        int days = Math.max(1, Math.min(requestedDays, MAX_DAYS));
        TrackedSite site = isWeb(contract) ? siteRepository.findByContract_Id(contractId).orElse(null) : null;
        if (site == null) {
            return new MetricsDashboardDTO(isWeb(contract), false, null, days,
                    "Todavía no hay datos: el estudiante tiene que instalar las métricas en tu web.",
                    0, 0, 0, null, emptyDays(days), List.of(), List.of());
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(days - 1L);
        List<MetricEvent> events = eventRepository.findBySite_IdAndCreatedAtGreaterThanEqual(site.getId(),
                firstDay.atStartOfDay());
        LocalDateTime last = eventRepository.findFirstBySite_IdOrderByCreatedAtDesc(site.getId())
                .map(MetricEvent::getCreatedAt).orElse(null);
        return summarize(events, days, firstDay, last);
    }

    static MetricsDashboardDTO summarize(List<MetricEvent> events, int days, LocalDate firstDay, LocalDateTime last) {
        List<MetricEvent> views = events.stream().filter(e -> MetricEvent.VIEW.equals(e.getType())).toList();
        List<MetricEvent> custom = events.stream().filter(e -> MetricEvent.EVENT.equals(e.getType())).toList();

        long visits = views.size();
        Set<String> sessions = new HashSet<>();
        views.forEach(e -> sessions.add(e.getSessionId() == null ? "sin-id-" + e.getId() : e.getSessionId()));
        long orders = custom.stream().filter(e -> ORDER.equals(e.getLabel())).count();
        Integer averageLoad = views.stream().filter(e -> e.getLoadMs() != null)
                .collect(Collectors.averagingInt(MetricEvent::getLoadMs)).intValue();
        if (views.stream().noneMatch(e -> e.getLoadMs() != null)) averageLoad = null;

        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) byDay.put(firstDay.plusDays(i), new long[2]);
        for (MetricEvent e : views) {
            long[] counts = byDay.get(e.getCreatedAt().toLocalDate());
            if (counts != null) counts[0]++;
        }
        for (MetricEvent e : custom) {
            long[] counts = byDay.get(e.getCreatedAt().toLocalDate());
            if (counts != null && ORDER.equals(e.getLabel())) counts[1]++;
        }
        List<MetricsDashboardDTO.Day> daily = byDay.entrySet().stream()
                .map(entry -> new MetricsDashboardDTO.Day(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();

        return new MetricsDashboardDTO(true, last != null, last, days,
                summary(visits, sessions.size(), orders, days),
                visits, sessions.size(), orders, averageLoad, daily,
                top(views, e -> e.getPath() == null ? "/" : e.getPath()),
                top(custom, MetricEvent::getLabel));
    }

    // «Tu web tuvo 340 visitas de 210 personas y 28 pedidos en los últimos 30 días.»
    static String summary(long visits, long visitors, long orders, int days) {
        if (visits == 0 && orders == 0) {
            return "Todavía no hay visitas en " + (days == 1 ? "el último día" : "los últimos " + days + " días") + ".";
        }
        String period = days == 1 ? "hoy" : "en los últimos " + days + " días";
        return "Tu web tuvo " + plural(visits, "visita", "visitas")
                + (visitors > 0 ? " de " + plural(visitors, "persona", "personas") : "")
                + (orders > 0 ? " y " + plural(orders, "pedido", "pedidos") : "")
                + " " + period + ".";
    }

    private static List<MetricsDashboardDTO.Count> top(List<MetricEvent> events, Function<MetricEvent, String> key) {
        return events.stream()
                .collect(Collectors.groupingBy(key, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> new MetricsDashboardDTO.Count(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<MetricsDashboardDTO.Day> emptyDays(int days) {
        LocalDate firstDay = LocalDate.now(clock).minusDays(days - 1L);
        List<MetricsDashboardDTO.Day> result = new ArrayList<>();
        for (int i = 0; i < days; i++) result.add(new MetricsDashboardDTO.Day(firstDay.plusDays(i), 0, 0));
        return result;
    }

    // ---------- Utilidades ----------

    private Contract findContractOfParticipant(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + contractId));
        Long userId = currentUserService.getCurrentUser().getId();
        boolean isClient = userId.equals(contract.getApplication().getProject().getClientId());
        boolean isStudent = userId.equals(contract.getApplication().getFreelancer().getUser().getId());
        if (!isClient && !isStudent) {
            throw new ForbiddenOperationException("No tienes acceso a este contrato");
        }
        return contract;
    }

    private static boolean isWeb(Contract contract) {
        return WEB.equalsIgnoreCase(contract.getApplication().getProject().getCategory());
    }

    private static String text(JsonNode root, String field, int max) {
        JsonNode value = root.path(field);
        if (!value.isString()) return null;
        String text = value.asString().trim();
        if (text.isEmpty()) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
