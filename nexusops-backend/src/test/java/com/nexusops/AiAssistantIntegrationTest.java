package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.ai.application.dto.ActionProposalDto;
import com.nexusops.ai.application.dto.ChatRequestDto;
import com.nexusops.ai.application.dto.ConfirmActionDto;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.UploadDocumentDto;
import com.nexusops.knowledge.domain.DocumentAccessScope;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.security.JwtTokenProvider;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AiAssistantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private RequestCategoryService categoryService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String tokenOrgA;
    private UUID orgIdA;
    private UserPrincipal principalA;

    private String tokenOrgB;
    private UUID orgIdB;

    @BeforeEach
    void setUp() {
        // Register Organization A
        String slugA = "ai-corp-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoA = new RegisterOrgRequestDto(
                "AI Corp A", slugA, "alice." + slugA + "@aicorp.com", "Password123!", "Alice", "Admin");
        AuthResponseDto authA = authService.registerOrganization(regDtoA);
        tokenOrgA = authA.accessToken();
        orgIdA = authA.organizationId();

        TenantContextHolder.setTenantId(orgIdA);

        Claims claimsA = jwtTokenProvider.getClaims(tokenOrgA);
        String membershipIdStr = claimsA.get("membership_id", String.class);
        UUID membershipIdA = membershipIdStr != null ? UUID.fromString(membershipIdStr) : authA.userId();

        List<SimpleGrantedAuthority> authoritiesA = List.of(
                new SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new SimpleGrantedAuthority("ORG_ADMIN_MANAGE"),
                new SimpleGrantedAuthority("REQUEST_CREATE"),
                new SimpleGrantedAuthority("REQUEST_VIEW_OWN"),
                new SimpleGrantedAuthority("APPROVAL_EXECUTE"),
                new SimpleGrantedAuthority("TASK_OPERATE")
        );
        principalA = new UserPrincipal(
                authA.userId(), authA.organizationId(), membershipIdA, authA.email(), authoritiesA);

        // Seed Department & Category in Org A
        departmentService.createDepartment(new CreateDepartmentDto("Engineering", "ENG", null, null));

        String laptopSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "urgencyReason": { "type": "string" },
                    "ramGb": { "type": "integer" },
                    "storage": { "type": "string" }
                  },
                  "required": ["urgencyReason"]
                }
                """;
        categoryService.createCategory(new CreateRequestCategoryDto(
                "LAPTOP", "Laptop Purchase", "Hardware procurement", "laptop", laptopSchema, true));

        // Register Organization B
        String slugB = "ai-corp-b-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoB = new RegisterOrgRequestDto(
                "AI Corp B", slugB, "bob." + slugB + "@aicorp.com", "Password123!", "Bob", "Admin");
        AuthResponseDto authB = authService.registerOrganization(regDtoB);
        tokenOrgB = authB.accessToken();
        orgIdB = authB.organizationId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldProvideGroundedPolicyAnswerWithCitations() throws Exception {
        TenantContextHolder.setTenantId(orgIdA);

        String policyText = """
                # Developer Hardware Refresh Policy 2026
                All engineering employees are eligible for a high-performance workstation.
                The maximum allowable developer laptop budget is $3,200.
                Hardware replacements are authorized every 3 years.
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "hardware-policy.md", "text/markdown", policyText.getBytes(StandardCharsets.UTF_8));

        knowledgeService.uploadDocument(
                orgIdA,
                principalA.getMembershipId(),
                new UploadDocumentDto("Developer Hardware Refresh Policy 2026", "Hardware rules", "POLICY", DocumentAccessScope.ALL, null),
                file
        );

        // Query Nexus AI Chat for Policy Information
        ChatRequestDto chatDto = new ChatRequestDto(null, "What is the developer laptop budget and refresh cycle?");
        String chatResponse = mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.citations").isArray())
                .andExpect(jsonPath("$.data.citations[0].documentTitle").value("Developer Hardware Refresh Policy 2026"))
                .andReturn().getResponse().getContentAsString();

        assertThat(chatResponse).contains("3,200");
    }

    @Test
    void shouldExecuteReadToolsToInspectUserRequests() throws Exception {
        TenantContextHolder.setTenantId(orgIdA);

        // Pre-create a request for user A
        var cat = categoryService.getActiveCategories().get(0);
        requestService.createRequest(new CreateRequestDto(
                cat.id(),
                null,
                "MacBook Pro 16-inch M3",
                "Needed for iOS engineering",
                RequestPriority.HIGH,
                Map.of("urgencyReason", "New joiner setup", "ramGb", 32, "storage", "1TB"),
                true
        ), principalA);

        // Ask AI about submitted requests
        ChatRequestDto chatDto = new ChatRequestDto(null, "Show my submitted requests");
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.toolUsed").value("searchMyRequests"))
                .andExpect(jsonPath("$.data.reply").value(org.hamcrest.Matchers.containsString("MacBook Pro 16-inch M3")));
    }

    @Test
    void shouldExecuteTwoPhaseConfirmationForWriteActions() throws Exception {
        TenantContextHolder.setTenantId(orgIdA);

        // 1. Propose request creation
        ChatRequestDto chatDto = new ChatRequestDto(null, "I need a high performance developer laptop");
        String chatResponse = mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proposal").isNotEmpty())
                .andExpect(jsonPath("$.data.proposal.toolName").value("createRequest"))
                .andExpect(jsonPath("$.data.proposal.status").value("PROPOSED"))
                .andReturn().getResponse().getContentAsString();

        String actionIdStr = objectMapper.readTree(chatResponse)
                .path("data").path("proposal").path("actionId").asText();
        UUID actionId = UUID.fromString(actionIdStr);

        // 2. Confirm the proposed action via Two-Phase Confirmation Endpoint
        ConfirmActionDto confirmDto = new ConfirmActionDto(true, "Confirmed by employee");
        mockMvc.perform(post("/api/v1/ai/actions/{id}/confirm", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestNumber").value(org.hamcrest.Matchers.startsWith("REQ-")))
                .andExpect(jsonPath("$.data.title").isNotEmpty());
    }

    @Test
    void shouldEnforceCrossTenantAiIsolation() throws Exception {
        TenantContextHolder.setTenantId(orgIdA);

        // User A proposes an action
        ChatRequestDto chatDto = new ChatRequestDto(null, "I need an ergonomic standing desk");
        String chatResponse = mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String convIdStr = objectMapper.readTree(chatResponse).path("data").path("conversationId").asText();
        String actionIdStr = objectMapper.readTree(chatResponse).path("data").path("proposal").path("actionId").asText();

        UUID convId = UUID.fromString(convIdStr);
        UUID actionId = UUID.fromString(actionIdStr);

        // User B (Tenant B) attempts to inspect Tenant A's conversation -> Expect 404 Not Found
        mockMvc.perform(get("/api/v1/ai/conversations/{id}", convId)
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isNotFound());

        // User B (Tenant B) attempts to confirm Tenant A's action proposal -> Expect 404 Not Found
        mockMvc.perform(post("/api/v1/ai/actions/{id}/confirm", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmActionDto(true, "Hacked confirmation")))
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isNotFound());
    }
}

