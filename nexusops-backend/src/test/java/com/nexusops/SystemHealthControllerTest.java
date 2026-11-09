package com.nexusops;

import com.nexusops.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHealth_shouldReturnSuccessEnvelopeAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/system/health")
                        .header("X-Correlation-Id", "test-corr-id-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "test-corr-id-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("NexusOps Backend"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}

