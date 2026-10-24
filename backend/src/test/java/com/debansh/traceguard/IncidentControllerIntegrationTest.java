package com.debansh.traceguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.debansh.traceguard.dto.TelemetryIngestRequest;
import com.debansh.traceguard.entity.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IncidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDeduplicateIdenticalStackTracesAndIncrementOccurrenceCount() throws Exception {
        TelemetryIngestRequest spike = new TelemetryIngestRequest(
                "order-service",
                "java.lang.IllegalStateException",
                "Duplicate order state transition",
                "POST /api/v1/orders/checkout",
                "java.lang.IllegalStateException: Duplicate transition\n\tat com.shop.OrderService.checkout(OrderService.java:42)",
                "{\"orderId\":101}",
                SeverityLevel.HIGH
        );

        String json = objectMapper.writeValueAsString(spike);

        // First ingestion creates group with occurrenceCount = 1
        mockMvc.perform(post("/api/v1/incidents/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(1));

        // Second identical ingestion deduplicates into same fingerprint with occurrenceCount = 2
        mockMvc.perform(post("/api/v1/incidents/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(2));
    }
}
