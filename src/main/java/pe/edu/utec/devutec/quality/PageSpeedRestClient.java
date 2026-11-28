package pe.edu.utec.devutec.quality;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// PageSpeed Insights de Google (gratis): mide rendimiento, accesibilidad, buenas prácticas y SEO en celular.
// Con PAGESPEED_API_KEY Google deja hacer muchas más consultas; sin key funciona, pero con un límite bajo.
@Slf4j
@Component
public class PageSpeedRestClient implements PageSpeedClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Categoría de Google → nombre en DEVUTEC
    static final Map<String, String> CATEGORIES = Map.of(
            "performance", "Rendimiento",
            "accessibility", "Accesibilidad",
            "best-practices", "Buenas prácticas",
            "seo", "SEO");
    private static final int ISSUES_PER_CATEGORY = 4;
    private static final Set<String> SCORED_MODES = Set.of("binary", "numeric", "metricSavings");
    // Errores de Lighthouse que significan que la web no se pudo abrir (eso sí lo tiene que arreglar el estudiante)
    private static final List<String> SITE_ERRORS = List.of(
            "DOCUMENT_REQUEST", "DNS_FAILURE", "NOT_HTML", "NO_FCP", "INVALID_URL", "PROTOCOL_TIMEOUT");

    private final String apiKey;
    private final RestClient restClient;

    public PageSpeedRestClient(@Value("${pagespeed.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        // Google suele tardar entre 10 y 40 segundos en medir una web
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public PageSpeedReport analyze(String url) {
        String body;
        try {
            body = restClient.get()
                    .uri(builder -> uri(builder, url))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String message = e.getResponseBodyAsString();
            if (SITE_ERRORS.stream().anyMatch(message::contains)) {
                throw new SiteNotReachableException(message);
            }
            log.warn("PageSpeed respondió {}: {}", e.getStatusCode(), shorten(message));
            throw new PageSpeedUnavailableException("PageSpeed respondió " + e.getStatusCode());
        } catch (RestClientException e) {
            log.warn("PageSpeed no respondió: {}", e.getMessage());
            throw new PageSpeedUnavailableException("PageSpeed no respondió");
        }
        try {
            return parse(JSON.readTree(body));
        } catch (JacksonException | NullPointerException e) {
            throw new PageSpeedUnavailableException("PageSpeed respondió algo que no se pudo leer");
        }
    }

    private URI uri(UriBuilder builder, String url) {
        UriBuilder b = builder.scheme("https").host("www.googleapis.com").path("/pagespeedonline/v5/runPagespeed")
                .queryParam("url", url)
                .queryParam("strategy", "mobile")
                .queryParam("locale", "es")
                .queryParam("category", "PERFORMANCE", "ACCESSIBILITY", "BEST_PRACTICES", "SEO");
        if (apiKey != null && !apiKey.isBlank()) {
            b.queryParam("key", apiKey);
        }
        return b.build();
    }

    // Lee los puntajes y, por categoría, los puntos peor evaluados
    static PageSpeedReport parse(JsonNode root) {
        JsonNode lighthouse = root.path("lighthouseResult");
        JsonNode categories = lighthouse.path("categories");
        JsonNode audits = lighthouse.path("audits");
        if (categories.isMissingNode() || categories.isEmpty()) {
            throw new PageSpeedUnavailableException("El informe no trae categorías");
        }

        List<PageSpeedReport.Issue> issues = new ArrayList<>();
        for (String key : List.of("performance", "accessibility", "best-practices", "seo")) {
            List<JsonNode> failing = new ArrayList<>();
            for (JsonNode ref : categories.path(key).path("auditRefs")) {
                JsonNode audit = audits.path(ref.path("id").asString(""));
                JsonNode score = audit.path("score");
                if (!score.isNumber() || !SCORED_MODES.contains(audit.path("scoreDisplayMode").asString(""))) continue;
                // En Rendimiento, las métricas (con peso) y las oportunidades más importantes; en el resto, lo que falla
                double limit = "performance".equals(key) && ref.path("weight").asDouble(0) == 0 ? 0.5 : 0.9;
                if (score.asDouble() < limit) failing.add(audit);
            }
            failing.stream()
                    .sorted(Comparator.comparingDouble(audit -> audit.path("score").asDouble()))
                    .limit(ISSUES_PER_CATEGORY)
                    .forEach(audit -> issues.add(new PageSpeedReport.Issue(
                            CATEGORIES.get(key),
                            audit.path("title").asString("").trim(),
                            audit.path("displayValue").asString("").trim())));
        }

        String finalUrl = lighthouse.path("finalDisplayedUrl").asString(lighthouse.path("finalUrl").asString(""));
        JsonNode httpsAudit = audits.path("is-on-https").path("score");
        boolean https = finalUrl.startsWith("https://") && (!httpsAudit.isNumber() || httpsAudit.asDouble() >= 1);

        return new PageSpeedReport(
                score(categories, "performance"),
                score(categories, "accessibility"),
                score(categories, "best-practices"),
                score(categories, "seo"),
                https,
                List.copyOf(issues));
    }

    private static int score(JsonNode categories, String key) {
        JsonNode score = categories.path(key).path("score");
        return score.isNumber() ? (int) Math.round(score.asDouble() * 100) : 0;
    }

    private static String shorten(String value) {
        return value == null || value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
