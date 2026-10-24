package com.debansh.traceguard.dto;

public record DashboardSummaryDto(
        long uniqueIncidentGroups,
        long totalRawEventsIngested,
        long deduplicationSavingsPercent,
        long openCriticalCount,
        long resolvedCount
) {}
