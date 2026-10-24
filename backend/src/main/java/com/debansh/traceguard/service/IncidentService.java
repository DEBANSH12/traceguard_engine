package com.debansh.traceguard.service;

import com.debansh.traceguard.dto.DashboardSummaryDto;
import com.debansh.traceguard.dto.ReplayRequest;
import com.debansh.traceguard.dto.TelemetryIngestRequest;
import com.debansh.traceguard.entity.IncidentGroup;
import com.debansh.traceguard.entity.IncidentStatus;
import com.debansh.traceguard.entity.SeverityLevel;
import com.debansh.traceguard.exception.IncidentNotFoundException;
import com.debansh.traceguard.repository.IncidentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Service
public class IncidentService {

    private final IncidentGroupRepository repository;
    private final FingerprintService fingerprintService;
    private final SseHubService sseHubService;
    private final GeminiTriageService geminiTriageService;
    private final Random random = new Random();

    public IncidentService(
            IncidentGroupRepository repository,
            FingerprintService fingerprintService,
            SseHubService sseHubService,
            GeminiTriageService geminiTriageService) {
        this.repository = repository;
        this.fingerprintService = fingerprintService;
        this.sseHubService = sseHubService;
        this.geminiTriageService = geminiTriageService;
    }

    @Transactional
    public IncidentGroup ingest(TelemetryIngestRequest req) {
        String fingerprint = fingerprintService.computeFingerprint(
                req.serviceName(), req.exceptionType(), req.endpointPath(), req.stackTrace());

        Instant now = Instant.now();
        IncidentGroup group = repository.findByFingerprint(fingerprint)
                .map(existing -> {
                    existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
                    existing.setLastSeenAt(now);
                    existing.setErrorMessage(req.errorMessage());
                    if (req.deadLetterPayload() != null && !req.deadLetterPayload().isBlank()) {
                        existing.setDeadLetterPayload(req.deadLetterPayload());
                    }
                    if (existing.getStatus() == IncidentStatus.RESOLVED) {
                        existing.setStatus(IncidentStatus.OPEN);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    IncidentGroup created = new IncidentGroup();
                    created.setFingerprint(fingerprint);
                    created.setServiceName(req.serviceName());
                    created.setExceptionType(req.exceptionType());
                    created.setErrorMessage(req.errorMessage());
                    created.setEndpointPath(req.endpointPath());
                    created.setSampleStackTrace(req.stackTrace());
                    created.setDeadLetterPayload(req.deadLetterPayload());
                    created.setSeverity(req.severity());
                    created.setStatus(IncidentStatus.OPEN);
                    created.setOccurrenceCount(1L);
                    created.setFirstSeenAt(now);
                    created.setLastSeenAt(now);
                    return created;
                });

        IncidentGroup saved = repository.save(group);
        sseHubService.broadcastIncidentUpdate(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<IncidentGroup> listAll(IncidentStatus status) {
        if (status != null) {
            return repository.findByStatusOrderByLastSeenAtDesc(status);
        }
        return repository.findAllByOrderByLastSeenAtDesc();
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        List<IncidentGroup> all = repository.findAll();
        long uniqueGroups = all.size();
        long totalEvents = repository.sumTotalOccurrences();
        long savings = totalEvents > 0 ? Math.round(((double) (totalEvents - uniqueGroups) / totalEvents) * 100) : 0;
        long openCritical = all.stream()
                .filter(i -> i.getStatus() != IncidentStatus.RESOLVED && i.getSeverity() == SeverityLevel.CRITICAL)
                .count();
        long resolved = all.stream()
                .filter(i -> i.getStatus() == IncidentStatus.RESOLVED)
                .count();

        return new DashboardSummaryDto(uniqueGroups, totalEvents, savings, openCritical, resolved);
    }

    @Transactional
    public IncidentGroup updateStatus(Long id, IncidentStatus status) {
        IncidentGroup incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        incident.setStatus(status);
        IncidentGroup saved = repository.save(incident);
        sseHubService.broadcastIncidentUpdate(saved);
        return saved;
    }

    @Transactional
    public IncidentGroup analyzeWithAi(Long id) {
        IncidentGroup incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));

        if (incident.getAiRootCause() == null || incident.getAiRootCause().isBlank()) {
            GeminiTriageService.AiDiagnosis diagnosis = geminiTriageService.analyzeIncident(incident);
            incident.setAiRootCause(diagnosis.rootCause());
            incident.setAiSuggestedPatch(diagnosis.suggestedPatch());
        }
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }
        IncidentGroup saved = repository.save(incident);
        sseHubService.broadcastIncidentUpdate(saved);
        return saved;
    }

    @Transactional
    public IncidentGroup replayDeadLetter(Long id, ReplayRequest replayRequest) {
        IncidentGroup incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));

        incident.setDeadLetterPayload(replayRequest.payload());
        long simulatedLatency = 38L + random.nextInt(45);
        incident.setLastReplayStatus(200);
        incident.setLastReplayLatencyMs(simulatedLatency);
        incident.setStatus(IncidentStatus.RESOLVED);

        IncidentGroup saved = repository.save(incident);
        sseHubService.broadcastIncidentUpdate(saved);
        return saved;
    }

    @Transactional
    public List<IncidentGroup> simulateProductionChaos() {
        List<TelemetryIngestRequest> sampleSpikes = List.of(
                new TelemetryIngestRequest(
                        "inventory-reservation-service",
                        "org.springframework.orm.ObjectOptimisticLockingFailureException",
                        "Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect): [com.shop.InventoryItem#4092]",
                        "POST /api/v1/inventory/reserve",
                        "org.springframework.orm.ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction\n"
                        + "\tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:307)\n"
                        + "\tat com.shop.inventory.ReservationService.reserveStock(ReservationService.java:84)\n"
                        + "\tat com.shop.inventory.InventoryController.reserve(InventoryController.java:49)",
                        "{\"sku\":\"GPU-RTX-5090\",\"warehouseId\":\"BLR-02\",\"quantity\":2,\"idempotencyKey\":\"req_98231a\"}",
                        SeverityLevel.CRITICAL
                ),
                new TelemetryIngestRequest(
                        "payment-settlement-worker",
                        "java.sql.SQLTransientConnectionException",
                        "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
                        "POST /api/v1/webhooks/razorpay-settlement",
                        "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.\n"
                        + "\tat com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)\n"
                        + "\tat com.shop.payment.SettlementService.reconcileWebhook(SettlementService.java:112)\n"
                        + "\tat com.shop.payment.WebhookController.ingest(WebhookController.java:63)",
                        "{\"event\":\"payment.captured\",\"orderId\":\"ORD-2026-8841\",\"amountPaise\":249900,\"currency\":\"INR\"}",
                        SeverityLevel.CRITICAL
                ),
                new TelemetryIngestRequest(
                        "identity-token-gateway",
                        "java.lang.NullPointerException",
                        "Cannot invoke \"String.split(String)\" because \"authorizationHeader\" is null",
                        "POST /api/v1/auth/refresh-token",
                        "java.lang.NullPointerException: Cannot invoke \"String.split(String)\" because \"authorizationHeader\" is null\n"
                        + "\tat com.shop.auth.JwtFilter.extractBearerToken(JwtFilter.java:58)\n"
                        + "\tat com.shop.auth.TokenController.refresh(TokenController.java:37)",
                        "{\"clientId\":\"mobile-android-v4\",\"grantType\":\"refresh_token\",\"deviceRegion\":\"IN-OD\"}",
                        SeverityLevel.HIGH
                )
        );

        ingest(sampleSpikes.get(0));
        ingest(sampleSpikes.get(0));
        ingest(sampleSpikes.get(0));
        ingest(sampleSpikes.get(1));
        ingest(sampleSpikes.get(1));
        ingest(sampleSpikes.get(2));

        return listAll(null);
    }
}
