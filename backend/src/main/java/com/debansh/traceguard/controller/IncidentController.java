package com.debansh.traceguard.controller;

import com.debansh.traceguard.dto.DashboardSummaryDto;
import com.debansh.traceguard.dto.ReplayRequest;
import com.debansh.traceguard.dto.TelemetryIngestRequest;
import com.debansh.traceguard.entity.IncidentGroup;
import com.debansh.traceguard.entity.IncidentStatus;
import com.debansh.traceguard.service.IncidentService;
import com.debansh.traceguard.service.SseHubService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final SseHubService sseHubService;

    public IncidentController(IncidentService incidentService, SseHubService sseHubService) {
        this.incidentService = incidentService;
        this.sseHubService = sseHubService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentGroup ingest(@Valid @RequestBody TelemetryIngestRequest request) {
        return incidentService.ingest(request);
    }

    @GetMapping
    public List<IncidentGroup> list(@RequestParam(required = false) IncidentStatus status) {
        return incidentService.listAll(status);
    }

    @GetMapping("/summary")
    public DashboardSummaryDto summary() {
        return incidentService.getSummary();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseHubService.subscribe();
    }

    @PatchMapping("/{id}/status")
    public IncidentGroup updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        IncidentStatus status = IncidentStatus.valueOf(body.getOrDefault("status", "OPEN"));
        return incidentService.updateStatus(id, status);
    }

    @PostMapping("/{id}/analyze")
    public IncidentGroup analyze(@PathVariable Long id) {
        return incidentService.analyzeWithAi(id);
    }

    @PostMapping("/{id}/replay")
    public IncidentGroup replay(@PathVariable Long id, @Valid @RequestBody ReplayRequest request) {
        return incidentService.replayDeadLetter(id, request);
    }

    @PostMapping("/simulate-chaos")
    public List<IncidentGroup> simulateChaos() {
        return incidentService.simulateProductionChaos();
    }
}
