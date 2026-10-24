package com.debansh.traceguard.dto;

import jakarta.validation.constraints.NotBlank;

public record ReplayRequest(
        @NotBlank(message = "payload is required") String payload,
        String targetEndpoint
) {}
