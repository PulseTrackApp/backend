package com.pulsetrack.backend.config;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie que la documentation OpenAPI est bien generee et decrit les endpoints
 * attendus. Ce test protege surtout d'une regression silencieuse : une montee de
 * version de springdoc incompatible casserait la doc sans casser l'API.
 */
class OpenApiDocumentationTest extends AbstractApiIntegrationTest {

    @Test
    void publie_le_document_openapi_avec_les_endpoints_de_l_api() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PulseTrack API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/profile']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workouts']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
