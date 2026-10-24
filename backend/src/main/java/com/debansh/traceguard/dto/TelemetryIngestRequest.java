package com.debansh.traceguard.dto;

import com.debansh.traceguard.entity.SeverityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TelemetryIngestRequest(
        @NotBlank(message = "serviceName is required") String serviceName,
        @NotBlank(message = "exceptionType is required") String exceptionType,
        @NotBlank(message = "errorMessage is required") String errorMessage,
        @NotBlank(message = "endpointPath is required") String endpointPath,
        @NotBlank(message = "stackTrace is required") String stackTrace,
        String deadLetterPayload,
        @NotNull(message = "severity is required") SeverityLevel severity
) {}
