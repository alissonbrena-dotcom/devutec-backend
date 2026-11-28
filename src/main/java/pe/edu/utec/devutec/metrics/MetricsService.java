package pe.edu.utec.devutec.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.model.contract.ContractStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Métricas DEVUTEC: el negocio ve cuántas personas visitan su web y, sobre todo, cuántas lo contactan, piden o reservan.
// 1. El cliente pide métricas al crear el proyecto (lo marca el asistente) y en el contrato da su consentimiento: se crea la llave.
// 2. El estudiante integra una línea de script (o hace POST desde su código) y la web manda eventos anónimos a /collect.
// 3. /collect revisa la llave y el dominio, suma un contador por día y tipo (para el panel básico, al instante)
//    y reenvía el evento a Amplitude, que es el motor del reporte Pro.
// 4. Si el cliente pidió y activó métricas, la Entrega final se bloquea hasta que llegue el primer dato.
// 5. Los 3 primeros meses son gratis desde el primer dato real; después, Básico (S/ 19) o Pro (S/ 39) al mes con Stripe.
// Nunca se guarda nada que identifique a quien visita la web (ni IP, ni navegador, ni cookies): Ley 29733.
@Slf4j
@Service
public class MetricsService {

    static final String CONSENT_VERSION = "2026-11";
    static final String CONSENT_TEXT = "Autorizo a DEVUTEC a recibir estadísticas anónimas de mi web (visitas, contactos, pedidos, "
            + "reservas y velocidad de carga) y a procesarlas con su proveedor de analítica (Amplitude) para mostrarme este panel. "
            + "No se guardan datos personales de mis visitantes: ni nombres, ni IP, ni cookies, y cada visitante decide con un aviso "
            + "si acepta ser contado. Puedo desactivarlo y borrar los datos de mi panel cuando quiera "
            + "(Ley 29733 de Protección de Datos Personales).";
    static final int TRIAL_DAYS = 90;
    static final int PANEL_DAYS = 30;
    static final int CHART_DAYS = 14;
    static final int MAX_PATH = 200;
    static final int MAX_VALUE_MS = 60_000;
    // Tope por llave y por visitante (IP) en un minuto, para que nadie llene la base de datos desde afuera.
    // La IP solo se usa en memoria para este conteo y nunca se guarda
    static final int MAX_EVENTS_PER_MINUTE = 120;
    static final Set<String> SOURCES = Set.of("instagram", "facebook", "google", "tiktok", "whatsapp", "directo", "otro");
    static final Set<String> DEVICES = Set.of("celular", "computadora");

    private final MetricsSiteRepository siteRepository;
    private final MetricDailyCountRepository countRepository;
    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;
    private final AmplitudeClient amplitude;
    private final MetricsStripeGateway stripe;
    private final String frontendUrl;
    private final String scriptUrl;
    private final String collectUrl;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, int[]> rateWindow = new ConcurrentHashMap<>();
    private final Map<Long, Object> siteLocks = new ConcurrentHashMap<>();
    private Clock clock = Clock.system(ZoneId.of("America/Lima"));

    public MetricsService(MetricsSiteRepository siteRepository,
                          MetricDailyCountRepository countRepository,
                          ContractRepository contractRepository,
                          CurrentUserService currentUserService,
                          AmplitudeClient amplitude,
                          MetricsStripeGateway stripe,
                          @Value("${app.frontend-url:https://devutec.vercel.app}") String frontendUrl,
                          @Value("${app.metrics.script-url:https://devutec.vercel.app/metricas.js}") String scriptUrl,
                          @Value("${app.metrics.collect-url:https://devutec.duckdns.org/api/v1/collect}") String collectUrl) {
        this.siteRepository = siteRepository;
        this.countRepository = countRepository;
        this.contractRepository = contractRepository;
        this.currentUserService = currentUserService;
        this.amplitude = amplitude;
        this.stripe = stripe;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.scriptUrl = scriptUrl;
        this.collectUrl = collectUrl;
    }

    // ---------- Panel del contrato ----------

    @Transactional(readOnly = true)
    public MetricsResponseDTO get(Long contractId) {
        Contract contract = findContract(contractId);
        boolean isClient = ensureParticipant(contract);
        return toResponse(contract, siteRepository.findByContractId(contractId).orElse(null), isClient);
    }

    @Transactional
    public MetricsResponseDTO enable(Long contractId) {
        Contract contract = findContract(contractId);
        User user = currentUserService.getCurrentUser();
        if (!user.getId().equals(clientId(contract))) {
            throw new ForbiddenOperationException("Solo el cliente puede activar las métricas de su web");
        }
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new InvalidOperationException("El contrato está cancelado");
        }
        MetricsSite site = siteRepository.findByContractId(contractId).orElseGet(() -> {
            MetricsSite created = new MetricsSite();
            created.setContractId(contractId);
            created.setPublicKey(newKey());
            created.setConsentedByUserId(user.getId());
            created.setConsentedAt(now());
            created.setConsentVersion(CONSENT_VERSION);
            return siteRepository.save(created);
        });
        return toResponse(contract, site, true);
    }

    // Desactivar retira el consentimiento: se borran la llave y los contadores del panel.
    // Lo que ya llegó a Amplitude no se puede borrar por web (los ids son por visita): está en las preguntas para el profe de derecho
    @Transactional
    public MetricsResponseDTO disable(Long contractId) {
        Contract contract = findContract(contractId);
        if (!currentUserService.getCurrentUser().getId().equals(clientId(contract))) {
            throw new ForbiddenOperationException("Solo el cliente puede desactivar las métricas de su web");
        }
        siteRepository.findByContractId(contractId).ifPresent(site -> {
            countRepository.deleteBySiteId(site.getId());
            siteRepository.delete(site);
        });
        return toResponse(contract, null, true);
    }

    // ---------- Lo que manda la web del cliente ----------

    // Devuelve false si el evento se ignoró. Nunca lanza error, así una web mal configurada no les muestra errores a sus visitantes.
    // origin: cabecera Origin del navegador (null si lo manda el código del estudiante desde su servidor) · ip: solo para el límite
    @Transactional
    public boolean collect(CollectRequestDTO request, String origin, String ip) {
        if (request == null || request.key() == null || request.type() == null) {
            return false;
        }
        MetricType type;
        try {
            type = MetricType.valueOf(request.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        Integer value = request.valueMs();
        if (type == MetricType.PERFORMANCE && (value == null || value < 0 || value > MAX_VALUE_MS)) {
            return false;
        }
        String name = type == MetricType.CUSTOM ? cleanName(request.name()) : null;
        if (type == MetricType.CUSTOM && name == null) {
            return false;
        }
        Optional<MetricsSite> found = siteRepository.findByPublicKey(request.key().trim());
        if (found.isEmpty() || !allow(found.get().getPublicKey(), ip)) {
            return false;
        }
        MetricsSite site = found.get();
        String cleanOrigin = cleanOrigin(origin);
        boolean test = cleanOrigin != null && isTestOrigin(cleanOrigin);

        synchronized (siteLocks.computeIfAbsent(site.getId(), id -> new Object())) {
            // El primer dato real fija el dominio; después solo se aceptan datos de ese dominio (y de los de prueba)
            if (cleanOrigin != null && !test) {
                if (site.getAllowedOrigin() == null) {
                    site.setAllowedOrigin(cleanOrigin);
                } else if (!site.getAllowedOrigin().equals(cleanOrigin)) {
                    return false;
                }
            }
            LocalDateTime now = now();
            if (site.getFirstEventAt() == null) {
                site.setFirstEventAt(now);
            }
            site.setLastEventAt(now);
            if (!test && site.getTrialEndsAt() == null) {
                site.setTrialEndsAt(now.plusDays(TRIAL_DAYS));
            }
            siteRepository.save(site);
            count(site.getId(), now.toLocalDate(), type, test, type == MetricType.PERFORMANCE ? value : 0);
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("web", site.getPublicKey());
        properties.put("contrato", site.getContractId());
        properties.put("pagina", cleanPath(request.path()));
        properties.put("prueba", test);
        if (value != null && type == MetricType.PERFORMANCE) {
            properties.put("carga_ms", value);
        }
        if (name != null) {
            properties.put("nombre", name);
        }
        String source = clean(request.source(), SOURCES);
        if (source != null) {
            properties.put("origen", source);
        }
        String device = clean(request.device(), DEVICES);
        if (device != null) {
            properties.put("dispositivo", device);
        }
        amplitude.send(new AmplitudeClient.Event(type.amplitudeName(), visitorId(request.visitorId()),
                clock.millis(), UUID.randomUUID().toString(), properties));
        return true;
    }

    // ---------- Entrega final ----------

    // Si el cliente pidió métricas y ya las activó, la Entrega final necesita que la web haya mandado al menos un dato.
    // Si las pidió pero nunca las activó, no se bloquea: sin llave el estudiante no puede integrarlas
    @Transactional(readOnly = true)
    public void checkFinalDelivery(Contract contract) {
        if (!requested(contract)) {
            return;
        }
        Optional<MetricsSite> site = siteRepository.findByContractId(contract.getId());
        if (site.isPresent() && site.get().getFirstEventAt() == null) {
            throw new InvalidOperationException("El cliente pidió métricas. Integra el script de DEVUTEC en la web y ábrela una vez "
                    + "antes de la Entrega final.");
        }
    }

    // ---------- Reporte Pro (Amplitude) ----------

    @Transactional(readOnly = true)
    public AdvancedReportDTO advanced(Long contractId) {
        Contract contract = findContract(contractId);
        ensureParticipant(contract);
        MetricsSite site = siteRepository.findByContractId(contractId).orElse(null);
        if (site == null) {
            return AdvancedReportDTO.unavailable("Primero hay que activar las métricas");
        }
        if (site.getPlan() != MetricsPlan.PRO) {
            return AdvancedReportDTO.unavailable("El reporte avanzado es parte del plan Pro");
        }
        if (!amplitude.canRead()) {
            return AdvancedReportDTO.unavailable("Amplitude todavía no está configurado en el servidor");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(PANEL_DAYS - 1L);
        try {
            String key = site.getPublicKey();
            return new AdvancedReportDTO(true, null,
                    items(amplitude.segmentation(MetricType.PAGEVIEW.amplitudeName(), key, start, today, "pagina", 1), 5),
                    items(amplitude.segmentation(MetricType.PAGEVIEW.amplitudeName(), key, start, today, "origen", 1), 6),
                    items(amplitude.segmentation(MetricType.PAGEVIEW.amplitudeName(), key, start, today, "dispositivo", 1), 2),
                    months(amplitude.segmentation(MetricType.CONTACT.amplitudeName(), key, today.minusMonths(11).withDayOfMonth(1),
                            today, null, 30)));
        } catch (RuntimeException e) {
            log.warn("Amplitude no respondió el reporte Pro del contrato {}: {}", contractId, e.getMessage());
            return AdvancedReportDTO.unavailable("Amplitude no respondió. Intenta en unos minutos.");
        }
    }

    // ---------- Membresía con Stripe ----------

    @Transactional(readOnly = true)
    public MetricsSubscriptionDTOs.StartResponse startSubscription(Long contractId, MetricsPlan plan) {
        Contract contract = findContract(contractId);
        if (!currentUserService.getCurrentUser().getId().equals(clientId(contract))) {
            throw new ForbiddenOperationException("Solo el cliente puede elegir el plan de sus métricas");
        }
        if (plan == null || plan == MetricsPlan.NONE) {
            throw new InvalidOperationException("Elige Básico o Pro");
        }
        MetricsSite site = siteRepository.findByContractId(contractId)
                .orElseThrow(() -> new InvalidOperationException("Primero activa las métricas de tu web"));
        if (site.getPlan() == plan) {
            throw new InvalidOperationException("Ya tienes el plan " + (plan == MetricsPlan.PRO ? "Pro" : "Básico"));
        }
        // Si todavía le quedan días gratis, Stripe recién cobra cuando terminan (Stripe pide al menos 2 días de anticipación)
        Long trialEnd = null;
        if (site.getTrialEndsAt() != null && site.getTrialEndsAt().isAfter(now().plusDays(2))) {
            trialEnd = site.getTrialEndsAt().atZone(clock.getZone()).toEpochSecond();
        }
        String page = frontendUrl + "/contracts/" + contractId + "/metricas";
        MetricsStripeGateway.SubscriptionSession session = stripe.createCheckout(contractId, plan, trialEnd,
                page + "?suscripcion=ok&session_id={CHECKOUT_SESSION_ID}", page + "?suscripcion=cancelada");
        return new MetricsSubscriptionDTOs.StartResponse(session.id(), session.url());
    }

    @Transactional
    public MetricsResponseDTO confirmSubscription(Long contractId, String sessionId) {
        Contract contract = findContract(contractId);
        if (!currentUserService.getCurrentUser().getId().equals(clientId(contract))) {
            throw new ForbiddenOperationException("Solo el cliente puede elegir el plan de sus métricas");
        }
        MetricsSite site = siteRepository.findByContractId(contractId)
                .orElseThrow(() -> new InvalidOperationException("Primero activa las métricas de tu web"));
        MetricsStripeGateway.SubscriptionSession session = stripe.getCheckout(sessionId);
        if (!String.valueOf(contractId).equals(session.contractId()) || session.plan() == null) {
            throw new InvalidOperationException("Esa sesión de pago no es de este contrato");
        }
        if (!"complete".equals(session.status())) {
            throw new InvalidOperationException("Todavía no vemos la suscripción. Si ya pagaste, espera unos segundos e intenta de nuevo.");
        }
        site.setPlan(MetricsPlan.valueOf(session.plan()));
        site.setStripeSubscriptionId(session.subscriptionId());
        site.setPlanStartedAt(now());
        siteRepository.save(site);
        return toResponse(contract, site, true);
    }

    // ---------- Armar la respuesta ----------

    private MetricsResponseDTO toResponse(Contract contract, MetricsSite site, boolean isClient) {
        boolean requested = requested(contract);
        if (site == null) {
            return new MetricsResponseDTO(false, requested, isClient, CONSENT_TEXT, null, null, false, null, null, false, null,
                    access(null), null, null);
        }
        MetricsResponseDTO.Access access = access(site);
        boolean integrated = site.getFirstEventAt() != null;
        return new MetricsResponseDTO(true, requested, isClient, CONSENT_TEXT, site.getPublicKey(), site.getConsentedAt(),
                integrated, site.getFirstEventAt(), site.getLastEventAt(), integrated && site.getTrialEndsAt() == null, site.getAllowedOrigin(),
                access, access.locked() ? null : summary(site), integration(site, contract));
    }

    private MetricsResponseDTO.Access access(MetricsSite site) {
        MetricsPlan plan = site == null ? MetricsPlan.NONE : site.getPlan();
        LocalDateTime trialEnds = site == null ? null : site.getTrialEndsAt();
        boolean trialActive = trialEnds != null && trialEnds.isAfter(now());
        // Sin plan, el panel se ve mientras la prueba no empieza (solo datos de prueba) o mientras dura
        boolean locked = site != null && plan == MetricsPlan.NONE && trialEnds != null && !trialActive;
        return new MetricsResponseDTO.Access(plan, trialEnds, trialActive, locked,
                MetricsPlan.BASIC.monthlyPrice(), MetricsPlan.PRO.monthlyPrice());
    }

    private MetricsResponseDTO.Summary summary(MetricsSite site) {
        LocalDate today = LocalDate.now(clock);
        Map<MetricType, long[]> totals = new EnumMap<>(MetricType.class);
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            perDay.put(today.minusDays(i), new long[2]);
        }
        boolean includesTest = false;
        for (MetricDailyCount row : countRepository.findBySiteIdAndDayGreaterThanEqual(site.getId(), today.minusDays(PANEL_DAYS - 1L))) {
            long[] sum = totals.computeIfAbsent(row.getType(), type -> new long[2]);
            sum[0] += row.getTotal();
            sum[1] += row.getSumValueMs();
            includesTest |= row.isTest();
            long[] day = perDay.get(row.getDay());
            if (day != null && row.getType() == MetricType.PAGEVIEW) {
                day[0] += row.getTotal();
            } else if (day != null && row.getType() == MetricType.CONTACT) {
                day[1] += row.getTotal();
            }
        }
        long[] speed = totals.getOrDefault(MetricType.PERFORMANCE, new long[2]);
        List<MetricsResponseDTO.Day> days = new ArrayList<>();
        perDay.forEach((date, values) -> days.add(new MetricsResponseDTO.Day(date, values[0], values[1])));
        return new MetricsResponseDTO.Summary(
                total(totals, MetricType.PAGEVIEW), total(totals, MetricType.CONTACT), total(totals, MetricType.ORDER),
                total(totals, MetricType.RESERVATION), total(totals, MetricType.CLICK),
                speed[0] == 0 ? null : (int) Math.round((double) speed[1] / speed[0]),
                includesTest, days);
    }

    private MetricsResponseDTO.Integration integration(MetricsSite site, Contract contract) {
        String key = site.getPublicKey();
        String tag = "<script src=\"" + scriptUrl + "\" data-key=\"" + key + "\" defer></script>";
        String post = "POST " + collectUrl + "\nContent-Type: text/plain\n\n"
                + "{\"key\":\"" + key + "\",\"type\":\"CONTACT\",\"path\":\"/contacto\"}";
        Project project = contract.getApplication().getProject();
        String category = project.getCategory() == null ? "WEB" : project.getCategory();
        String what = switch (category) {
            case "APP" -> "Es una app. Desde su backend, manda un POST a " + collectUrl + " cuando pase algo importante: "
                    + "abrir la app (type PAGEVIEW con el nombre de la pantalla en path), contactos (CONTACT), pedidos (ORDER), "
                    + "reservas (RESERVATION) o un evento propio (type CUSTOM con name, por ejemplo \"pedido_entregado\").";
            case "AUTOMATIZACION" -> "Es una automatización. Al terminar cada ejecución manda un POST a " + collectUrl
                    + " con type CUSTOM y name \"ejecucion\" (o \"error\" si falló). Si ahorra tiempo, usa valueMs con los milisegundos "
                    + "que tardó, con type PERFORMANCE.";
            default -> "Es una web. Pega esta línea antes de </body> en todas las páginas: " + tag + "\n"
                    + "El script ya cuenta solo las visitas, los clics y la velocidad. Marca los botones importantes con un atributo: "
                    + "data-devutec=\"contacto\" en WhatsApp, llamar, cómo llegar y formularios; data-devutec=\"pedido\" en el botón de pedir; "
                    + "data-devutec=\"reserva\" en el de reservar.";
        };
        String prompt = "Integra las métricas de DEVUTEC en el proyecto \"" + project.getTitle() + "\". La llave de este proyecto es "
                + key + ". " + what + "\n"
                + "Reglas: no envíes nombres, correos, teléfonos ni nada que identifique a una persona; no agregues otras herramientas "
                + "de analítica ni cookies. Cada POST es un JSON con key, type y path (y valueMs o name si corresponde), enviado como "
                + "text/plain. Al terminar, abre la web o ejecuta la app una vez: en el contrato debe aparecer \"Integrada, ya recibimos datos\".";
        return new MetricsResponseDTO.Integration(scriptUrl, tag, collectUrl, post, prompt);
    }

    // ---------- Ayudas ----------

    private void count(Long siteId, LocalDate day, MetricType type, boolean test, int valueMs) {
        MetricDailyCount row = countRepository.findBySiteIdAndDayAndTypeAndTest(siteId, day, type, test).orElseGet(() -> {
            MetricDailyCount created = new MetricDailyCount();
            created.setSiteId(siteId);
            created.setDay(day);
            created.setType(type);
            created.setTest(test);
            return created;
        });
        row.setTotal(row.getTotal() + 1);
        row.setSumValueMs(row.getSumValueMs() + valueMs);
        countRepository.save(row);
    }

    private boolean allow(String key, String ip) {
        long minute = clock.millis() / 60_000;
        String bucket = key + "|" + (ip == null ? "-" : ip);
        int[] window = rateWindow.compute(bucket, (k, current) ->
                current == null || current[0] != (int) minute ? new int[]{(int) minute, 0} : current);
        if (rateWindow.size() > 50_000) {
            rateWindow.clear();
        }
        synchronized (window) {
            window[1]++;
            return window[1] <= MAX_EVENTS_PER_MINUTE;
        }
    }

    // "https://Pizzeria.pe:443/" → "https://pizzeria.pe"; null si no hay Origin o no es válido
    static String cleanOrigin(String origin) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return null;
        }
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1 || (port == 443 && "https".equals(uri.getScheme())) || (port == 80 && "http".equals(uri.getScheme()));
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Datos de prueba: localhost, links de vista previa de Vercel (llevan "-git-") y la página de prueba de DEVUTEC
    boolean isTestOrigin(String origin) {
        String host = URI.create(origin).getHost();
        return host.equals("localhost") || host.equals("127.0.0.1")
                || (host.endsWith(".vercel.app") && host.contains("-git-"))
                || origin.equals(cleanOrigin(frontendUrl));
    }

    // "/carta?mesa=4#arriba" → "/carta". Lo que va después de ? o # se descarta porque ahí a veces viajan datos personales
    static String cleanPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String clean = path.trim();
        int query = clean.indexOf('?');
        int hash = clean.indexOf('#');
        int cut = query < 0 ? hash : hash < 0 ? query : Math.min(query, hash);
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        return clean.length() > MAX_PATH ? clean.substring(0, MAX_PATH) : clean;
    }

    // Nombre de evento propio: solo letras, números y _ (máximo 40), para que nadie meta datos personales ahí
    static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        clean = clean.length() > 40 ? clean.substring(0, 40) : clean;
        return clean.replace("_", "").isEmpty() ? null : clean;
    }

    private static String clean(String value, Set<String> allowed) {
        if (value == null) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(lower) ? lower : null;
    }

    // Amplitude pide un identificador por evento: se usa el de la visita (al azar, solo si el visitante aceptó) o uno nuevo
    private String visitorId(String fromScript) {
        if (fromScript != null && fromScript.matches("[A-Za-z0-9_-]{8,64}")) {
            return "v_" + fromScript;
        }
        return "v_" + UUID.randomUUID();
    }

    private static long total(Map<MetricType, long[]> totals, MetricType type) {
        long[] sum = totals.get(type);
        return sum == null ? 0 : sum[0];
    }

    private static List<AdvancedReportDTO.Item> items(AmplitudeClient.Segmentation result, int limit) {
        List<AdvancedReportDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < result.labels().size(); i++) {
            items.add(new AdvancedReportDTO.Item(result.labels().get(i), result.totals().get(i)));
        }
        items.sort((a, b) -> Long.compare(b.total(), a.total()));
        return items.size() > limit ? items.subList(0, limit) : items;
    }

    private static List<AdvancedReportDTO.Item> months(AmplitudeClient.Segmentation result) {
        List<AdvancedReportDTO.Item> items = new ArrayList<>();
        List<Long> values = result.perDay().isEmpty() ? List.of() : result.perDay().get(0);
        for (int i = 0; i < result.days().size() && i < values.size(); i++) {
            String day = result.days().get(i);
            items.add(new AdvancedReportDTO.Item(day.length() >= 7 ? day.substring(0, 7) : day, values.get(i)));
        }
        return items;
    }

    private String newKey() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return "dv_" + HexFormat.of().formatHex(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Contract findContract(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + id));
    }

    // true si es el cliente; lanza error si no es ni el cliente ni el freelancer del contrato
    private boolean ensureParticipant(Contract contract) {
        Long userId = currentUserService.getCurrentUser().getId();
        if (userId.equals(clientId(contract))) {
            return true;
        }
        if (userId.equals(contract.getApplication().getFreelancer().getUser().getId())) {
            return false;
        }
        throw new ForbiddenOperationException("Solo el cliente y el freelancer del contrato ven sus métricas");
    }

    private static Long clientId(Contract contract) {
        return contract.getApplication().getProject().getClientId();
    }

    // El cliente pidió métricas al crear el proyecto (campo de Nicole, lo marca el asistente o el formulario)
    private static boolean requested(Contract contract) {
        return Boolean.TRUE.equals(contract.getApplication().getProject().getMetricsRequested());
    }

    // Para las pruebas: fija la hora
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
