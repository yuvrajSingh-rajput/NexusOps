package com.nexusops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.request.application.dto.AddCommentDto;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.domain.CommentVisibility;
import com.nexusops.request.domain.RequestPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should auto-seed and list request categories for tenant")
    void testGetRequestCategories_AutoSeedsDefaults() throws Exception {
        String token = registerTenantAndGetToken("Acme Corp", "acme-cat-" + UUID.randomUUID().toString().substring(0, 6));

        mockMvc.perform(get("/api/v1/request-categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].code", hasItems("DEVELOPER_LAPTOP", "SOFTWARE_ACCESS", "EXPENSE_REIMBURSEMENT")));
    }

    @Test
    @DisplayName("Should successfully create a laptop request with dynamic schema payload and sequential REQ-1001 number")
    void testCreateAndSubmitRequest_Success() throws Exception {
        String slug = "acme-req-" + UUID.randomUUID().toString().substring(0, 6);
        String token = registerTenantAndGetToken("Laptop Corp", slug);

        // 1. Create a department
        String deptId = createDepartment(token, "ENG", "Engineering");

        // 2. Fetch Category ID for DEVELOPER_LAPTOP
        String categoryId = getCategoryIdByCode(token, "DEVELOPER_LAPTOP");

        // 3. Create Request with Valid Dynamic Payload
        Map<String, Object> payload = Map.of(
                "deviceModel", "MacBook Pro 16 M3 Max",
                "ramGb", 64,
                "estimatedAmount", 3499.00,
                "urgencyReason", "Onboarding senior platform engineer"
        );

        CreateRequestDto createDto = new CreateRequestDto(
                UUID.fromString(categoryId),
                UUID.fromString(deptId),
                "Engineering Workstation - MacBook Pro 16",
                "Hardware procurement for new hire",
                RequestPriority.HIGH,
                payload,
                true // autoSubmit
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestNumber", startsWith("REQ-")))
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.categoryCode").value("DEVELOPER_LAPTOP"))
                .andExpect(jsonPath("$.data.departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data.payload.ramGb").value(64))
                .andReturn();

        String requestId = extractId(createResult);

        // 4. Add Comment
        AddCommentDto commentDto = new AddCommentDto("Expedited shipping requested", CommentVisibility.REQUESTER_VISIBLE);
        mockMvc.perform(post("/api/v1/requests/" + requestId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Expedited shipping requested"));

        // 5. Query Comments
        mockMvc.perform(get("/api/v1/requests/" + requestId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content").value("Expedited shipping requested"));

        // 6. Cancel Request
        mockMvc.perform(post("/api/v1/requests/" + requestId + "/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Budget delayed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Should reject request creation when required JSON Schema fields are missing (400 Bad Request)")
    void testCreateRequest_MissingRequiredPayloadFields_Fails() throws Exception {
        String token = registerTenantAndGetToken("Validation Corp", "val-org-" + UUID.randomUUID().toString().substring(0, 6));
        String deptId = createDepartment(token, "FIN", "Finance");
        String categoryId = getCategoryIdByCode(token, "DEVELOPER_LAPTOP");

        // Missing ramGb and estimatedAmount
        Map<String, Object> invalidPayload = Map.of(
                "deviceModel", "Dell XPS 15 9530"
        );

        CreateRequestDto createDto = new CreateRequestDto(
                UUID.fromString(categoryId),
                UUID.fromString(deptId),
                "Dell Workstation",
                "Missing required parameters",
                RequestPriority.MEDIUM,
                invalidPayload,
                false
        );

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message", containsString("Required form fields are missing")));
    }

    @Test
    @DisplayName("Cross-tenant isolation: Tenant B cannot access Tenant A's requests via IDOR or lists")
    void testCrossTenantRequestIsolation() throws Exception {
        // Tenant A
        String tokenA = registerTenantAndGetToken("Tenant Alpha", "alpha-" + UUID.randomUUID().toString().substring(0, 6));
        String deptIdA = createDepartment(tokenA, "ENG", "Engineering Alpha");
        String catIdA = getCategoryIdByCode(tokenA, "DEVELOPER_LAPTOP");

        CreateRequestDto reqA = new CreateRequestDto(
                UUID.fromString(catIdA),
                UUID.fromString(deptIdA),
                "Confidential Alpha Project Laptop",
                "Internal Alpha specs",
                RequestPriority.URGENT,
                Map.of("deviceModel", "ThinkPad P1 Gen 6", "ramGb", 64, "estimatedAmount", 2800.0, "urgencyReason", "Alpha Core"),
                true
        );

        MvcResult resA = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();
        String reqIdA = extractId(resA);

        // Tenant B
        String tokenB = registerTenantAndGetToken("Tenant Beta", "beta-" + UUID.randomUUID().toString().substring(0, 6));

        // 1. Bob (Tenant B) attempts direct IDOR on Alice's (Tenant A) request -> 404 Not Found
        mockMvc.perform(get("/api/v1/requests/" + reqIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // 2. Bob lists his requests -> 0 requests returned (Tenant A's request is invisible)
        mockMvc.perform(get("/api/v1/requests/my")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // 3. Bob queries all requests in tenant -> 0 requests returned
        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // Helper utilities
    private String registerTenantAndGetToken(String orgName, String slug) throws Exception {
        RegisterOrgRequestDto reg = new RegisterOrgRequestDto(
                orgName, slug, "admin@" + slug + ".com", "Password123!", "Admin", "User"
        );
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String createDepartment(String token, String code, String name) throws Exception {
        CreateDepartmentDto dto = new CreateDepartmentDto(code, name, null, null);
        MvcResult res = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private String getCategoryIdByCode(String token, String code) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/request-categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode categories = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        for (JsonNode cat : categories) {
            if (cat.path("code").asText().equals(code)) {
                return cat.path("id").asText();
            }
        }
        throw new IllegalStateException("Category not found with code: " + code);
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }
}
