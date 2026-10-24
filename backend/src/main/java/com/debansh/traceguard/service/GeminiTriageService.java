package com.debansh.traceguard.service;

import com.debansh.traceguard.entity.IncidentGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiTriageService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiTriageService(
            ObjectMapper objectMapper,
            @Value("${traceguard.gemini.api-key:}") String apiKey,
            @Value("${traceguard.gemini.model:gemini-2.5-flash}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public record AiDiagnosis(String rootCause, String suggestedPatch) {}

    public AiDiagnosis analyzeIncident(IncidentGroup incident) {
        if (apiKey == null || apiKey.isBlank()) {
            return buildDeterministicDiagnosis(incident);
        }

        try {
            String prompt = "You are a Principal Java Site Reliability Engineer. "
                    + "Analyze this production exception and return ONLY valid JSON with two keys: "
                    + "\"rootCause\" (2 concise sentences explaining why this happened) and "
                    + "\"suggestedPatch\" (a concise Java/Spring Boot code fix). "
                    + "Service: " + incident.getServiceName()
                    + ", Endpoint: " + incident.getEndpointPath()
                    + ", Exception: " + incident.getExceptionType() + " - " + incident.getErrorMessage()
                    + ", Stack Trace:\n" + incident.getSampleStackTrace();

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            );

            String rawResponse = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String jsonText = root.at("/candidates/0/content/parts/0/text").asText();
            JsonNode parsed = objectMapper.readTree(jsonText);
            return new AiDiagnosis(
                    parsed.path("rootCause").asText("Root cause analyzed via Gemini API."),
                    parsed.path("suggestedPatch").asText("// Review stack frame constraints")
            );
        } catch (Exception ex) {
            return buildDeterministicDiagnosis(incident);
        }
    }

    private AiDiagnosis buildDeterministicDiagnosis(IncidentGroup incident) {
        String exType = incident.getExceptionType();
        if (exType.contains("OptimisticLocking")) {
            return new AiDiagnosis(
                    "Concurrent transactions attempted to update the same inventory row version simultaneously without retry backoff, causing a Hibernate @Version conflict.",
                    "@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 80, multiplier = 2.0))\n"
                    + "@Transactional\n"
                    + "public ReservationResult reserveStock(OrderCommand cmd) {\n"
                    + "    InventoryItem item = repo.findBySku(cmd.sku()).orElseThrow();\n"
                    + "    item.decrementAvailable(cmd.quantity());\n"
                    + "    return ReservationResult.success(item);\n"
                    + "}"
            );
        } else if (exType.contains("SQLTransientConnection") || exType.contains("ConnectionTimeout")) {
            return new AiDiagnosis(
                    "HikariCP connection pool exhausted due to slow downstream HTTP calls executing inside an open @Transactional database boundary.",
                    "// Move external REST call outside @Transactional boundary\n"
                    + "public PaymentReceipt processCheckout(CheckoutRequest req) {\n"
                    + "    GatewayResponse gateway = paymentClient.chargeWithTimeout(req, Duration.ofSeconds(2));\n"
                    + "    return transactionTemplate.execute(status -> persistLedgerEntry(req, gateway));\n"
                    + "}"
            );
        }
        return new AiDiagnosis(
                "Unhandled null or schema contract mismatch in " + incident.getServiceName() + " at " + incident.getEndpointPath() + " when deserializing upstream payload.",
                "// Enforce Bean Validation & null-safe header guard before domain execution\n"
                + "public ResponseEntity<TokenAck> refresh(@Valid @RequestBody RefreshRequest req, @RequestHeader(value = \"Authorization\", required = false) String authHeader) {\n"
                + "    if (authHeader == null || !authHeader.startsWith(\"Bearer \")) {\n"
                + "        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, \"Missing Bearer token\");\n"
                + "    }\n"
                + "    return ResponseEntity.ok(tokenService.rotateRefreshToken(req, authHeader.substring(7)));\n"
                + "}"
        );
    }
}
