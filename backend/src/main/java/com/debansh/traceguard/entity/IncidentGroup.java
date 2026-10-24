package com.debansh.traceguard.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incident_groups", indexes = {
    @Index(name = "idx_incident_fingerprint", columnList = "fingerprint", unique = true),
    @Index(name = "idx_incident_status_last_seen", columnList = "status, lastSeenAt DESC")
})
public class IncidentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Column(nullable = false, length = 160)
    private String exceptionType;

    @Column(nullable = false, length = 500)
    private String errorMessage;

    @Column(nullable = false, length = 250)
    private String endpointPath;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sampleStackTrace;

    @Column(columnDefinition = "TEXT")
    private String deadLetterPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeverityLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(nullable = false)
    private long occurrenceCount;

    @Column(nullable = false)
    private Instant firstSeenAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(columnDefinition = "TEXT")
    private String aiRootCause;

    @Column(columnDefinition = "TEXT")
    private String aiSuggestedPatch;

    private Integer lastReplayStatus;
    private Long lastReplayLatencyMs;

    public IncidentGroup() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }
    public String getSampleStackTrace() { return sampleStackTrace; }
    public void setSampleStackTrace(String sampleStackTrace) { this.sampleStackTrace = sampleStackTrace; }
    public String getDeadLetterPayload() { return deadLetterPayload; }
    public void setDeadLetterPayload(String deadLetterPayload) { this.deadLetterPayload = deadLetterPayload; }
    public SeverityLevel getSeverity() { return severity; }
    public void setSeverity(SeverityLevel severity) { this.severity = severity; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public long getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getAiRootCause() { return aiRootCause; }
    public void setAiRootCause(String aiRootCause) { this.aiRootCause = aiRootCause; }
    public String getAiSuggestedPatch() { return aiSuggestedPatch; }
    public void setAiSuggestedPatch(String aiSuggestedPatch) { this.aiSuggestedPatch = aiSuggestedPatch; }
    public Integer getLastReplayStatus() { return lastReplayStatus; }
    public void setLastReplayStatus(Integer lastReplayStatus) { this.lastReplayStatus = lastReplayStatus; }
    public Long getLastReplayLatencyMs() { return lastReplayLatencyMs; }
    public void setLastReplayLatencyMs(Long lastReplayLatencyMs) { this.lastReplayLatencyMs = lastReplayLatencyMs; }
}
