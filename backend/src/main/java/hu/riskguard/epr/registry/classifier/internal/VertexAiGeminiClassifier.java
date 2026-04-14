package hu.riskguard.epr.registry.classifier.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import hu.riskguard.epr.registry.classifier.ClassificationConfidence;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.classifier.KfSuggestion;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gemini 2.5 Flash classifier via Vertex AI REST API.
 *
 * <p>Auth: Google Application Default Credentials (ADC).
 *   - Cloud Run: workload identity (automatic).
 *   - Local dev: {@code gcloud auth application-default login}.
 *   No API key stored in config.
 *
 * <p>Wrapped in a Resilience4j circuit breaker ({@code vertex-gemini}).
 * When the circuit is open, {@link CallNotPermittedException} is caught and
 * {@link ClassificationResult#empty()} is returned silently.
 *
 * <p>Spring AI 1.x is not compatible with Spring Boot 4.0.3 (Spring Framework 7.x).
 * This class uses {@code java.net.http.HttpClient} + google-auth-library for ADC tokens
 * instead of the Spring AI starter. Config keys under {@code spring.ai.vertex.ai.gemini.*}
 * are still present in {@code application.yml} for future compatibility.
 */
@Component
public class VertexAiGeminiClassifier implements KfCodeClassifierService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiGeminiClassifier.class);
    private static final String SCOPES = "https://www.googleapis.com/auth/cloud-platform";

    private final String projectId;
    private final String location;
    private final String model;
    private final CircuitBreaker circuitBreaker;
    private final String systemPrompt;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile GoogleCredentials credentials;
    private volatile boolean credentialsUnavailable;

    public VertexAiGeminiClassifier(
            @Value("${spring.ai.vertex.ai.gemini.project-id:risk-guard-dev}") String projectId,
            @Value("${spring.ai.vertex.ai.gemini.location:europe-west1}") String location,
            @Value("${spring.ai.vertex.ai.gemini.chat.options.model:gemini-2.5-flash}") String model,
            CircuitBreakerRegistry circuitBreakerRegistry) throws IOException {
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("vertex-gemini");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();

        String systemPromptBase = new ClassPathResource("prompts/kf-classifier-system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        String taxonomy = new ClassPathResource("prompts/kf-taxonomy-excerpt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        this.systemPrompt = systemPromptBase + "\n\n" + taxonomy;
    }

    @Override
    public ClassificationResult classify(String productName, String vtsz) {
        try {
            return circuitBreaker.executeSupplier(() -> doClassify(productName, vtsz));
        } catch (CallNotPermittedException e) {
            log.debug("Vertex AI circuit breaker open — degrading to empty result");
            return ClassificationResult.empty();
        }
    }

    private ClassificationResult doClassify(String productName, String vtsz) {
        try {
            String token = getAccessToken();
            String requestBody = buildRequestJson(productName, vtsz);

            String url = String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                    location, projectId, location, model
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Vertex AI returned HTTP {}: {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return ClassificationResult.empty();
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            log.warn("Vertex AI Gemini classification failed: {}", e.getMessage());
            return ClassificationResult.empty();
        }
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials creds = resolveCredentials();
        if (creds == null) {
            throw new IOException("Google Application Default Credentials unavailable");
        }
        creds.refreshIfExpired();
        return creds.getAccessToken().getTokenValue();
    }

    private GoogleCredentials resolveCredentials() {
        if (credentials != null) return credentials;
        if (credentialsUnavailable) return null;
        synchronized (this) {
            if (credentials != null) return credentials;
            if (credentialsUnavailable) return null;
            try {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
            } catch (IOException e) {
                log.warn("ADC unavailable — Vertex AI classifier will degrade to empty result: {}", e.getMessage());
                credentialsUnavailable = true;
            }
            return credentials;
        }
    }

    private String buildRequestJson(String productName, String vtsz) throws Exception {
        String userMessage = "Product name: " + productName + "\nVTSZ code: " + (vtsz != null ? vtsz : "not available");

        var systemParts = objectMapper.createArrayNode();
        systemParts.add(objectMapper.createObjectNode().put("text", systemPrompt));

        var systemInstruction = objectMapper.createObjectNode();
        systemInstruction.set("parts", systemParts);

        var userParts = objectMapper.createArrayNode();
        userParts.add(objectMapper.createObjectNode().put("text", userMessage));

        var userContent = objectMapper.createObjectNode();
        userContent.put("role", "user");
        userContent.set("parts", userParts);

        var contents = objectMapper.createArrayNode();
        contents.add(userContent);

        var generationConfig = objectMapper.createObjectNode();
        generationConfig.put("responseMimeType", "application/json");

        var root = objectMapper.createObjectNode();
        root.set("system_instruction", systemInstruction);
        root.set("contents", contents);
        root.set("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(root);
    }

    private ClassificationResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return ClassificationResult.empty();
            }

            String text = candidates.get(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return ClassificationResult.empty();
            }

            // Strip markdown code fences if present
            text = text.strip();
            if (text.startsWith("```")) {
                text = text.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            List<JsonNode> rawSuggestions = objectMapper.readValue(text, new TypeReference<List<JsonNode>>() {});
            if (rawSuggestions == null || rawSuggestions.isEmpty()) {
                return ClassificationResult.empty();
            }

            List<KfSuggestion> suggestions = new ArrayList<>();
            for (JsonNode node : rawSuggestions) {
                String kfCode = node.path("kfCode").asText(null);
                String description = node.path("description").asText("");
                double score = node.path("score").asDouble(0.0);
                if (kfCode != null && !kfCode.isBlank()) {
                    suggestions.add(new KfSuggestion(kfCode, List.of(description), score));
                }
                if (suggestions.size() >= 3) break;
            }

            if (suggestions.isEmpty()) {
                return ClassificationResult.empty();
            }

            suggestions.sort((a, b) -> Double.compare(b.score(), a.score()));

            double topScore = suggestions.get(0).score();
            ClassificationConfidence confidence;
            if (topScore >= 0.80) {
                confidence = ClassificationConfidence.HIGH;
            } else if (topScore >= 0.50) {
                confidence = ClassificationConfidence.MEDIUM;
            } else {
                confidence = ClassificationConfidence.LOW;
            }

            return new ClassificationResult(
                    Collections.unmodifiableList(suggestions),
                    ClassificationStrategy.VERTEX_GEMINI,
                    confidence,
                    model,
                    Instant.now()
            );

        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return ClassificationResult.empty();
        }
    }
}
