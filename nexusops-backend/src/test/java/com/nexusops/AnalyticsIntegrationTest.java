package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.ai.application.dto.ChatRequestDto;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.events.EventPublisher;
import com.nexusops.shared.security.JwtTokenProvider;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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
public class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private RequestCategoryService categoryService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EventPublisher eventPublisher;

    private String tokenOrgA;
    private UUID orgIdA;
    private UserPrincipal principalA;
    private UUID categoryIdA;

    private String tokenOrgB;
    private UUID orgIdB;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(eventPublisher);

        // Setup Tenant A
        String slugA = "analytics-a-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoA = new RegisterOrgRequestDto(
                "Analytics Corp A", slugA, "alice." + slugA + "@analytics.com", "Password123!", "Alice", "Admin");
        AuthResponseDto authA = authService.registerOrganization(regDtoA);
        tokenOrgA = authA.accessToken();
        orgIdA = authA.organizationId();

        TenantContextHolder.setTenantId(orgIdA);

        Claims claimsA = jwtTokenProvider.getClaims(tokenOrgA);
        UUID membershipIdA = UUID.fromString(claimsA.get("membership_id", String.class));

        List<SimpleGrantedAuthority> authoritiesA = List.of(
                new SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new SimpleGrantedAuthority("ORG_ADMIN_MANAGE"),
                new SimpleGrantedAuthority("REQUEST_CREATE"),
                new SimpleGrantedAuthority("REQUEST_VIEW_OWN"),
                new SimpleGrantedAuthority("APPROVAL_EXECUTE"),
                new SimpleGrantedAuthority("TASK_OPERATE")
        );
        principalA = new UserPrincipal(authA.userId(), authA.organizationId(), membershipIdA, authA.email(), authoritiesA);

        var deptA = departmentService.createDepartment(new CreateDepartmentDto("ENG", "Engineering", null, null));
        UUID engDeptId = deptA.id();

        String schema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "item": { "type": "string" },
                    "budget": { "type": "number" }
                  },
                  "required": ["item"]
                }
                """;
        var catA = categoryService.createCategory(new CreateRequestCategoryDto(
                "HARDWARE", "Hardware Request", "Procurement requests", "laptop", schema, true));
        categoryIdA = catA.id();

        // Seed 2 Requests for Tenant A in Engineering department
        CreateRequestDto req1 = new CreateRequestDto(
                categoryIdA,
                engDeptId,
                "Dev Laptop Request",
                "M3 Max MacBook Pro",
                RequestPriority.HIGH,
                Map.of("item", "MacBook Pro", "budget", 3500.00),
                true
        );
        requestService.createRequest(req1, principalA);

        CreateRequestDto req2 = new CreateRequestDto(
                categoryIdA,
                engDeptId,
                "External 4K Monitor",
                "Dell UltraSharp 32",
                RequestPriority.MEDIUM,
                Map.of("item", "Dell Monitor", "budget", 800.00),
                true
        );
        requestService.createRequest(req2, principalA);

        // Setup Tenant B (Empty / Isolated)
        String slugB = "analytics-b-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoB = new RegisterOrgRequestDto(
                "Analytics Corp B", slugB, "bob." + slugB + "@analytics.com", "Password123!", "Bob", "Admin");
        AuthResponseDto authB = authService.registerOrganization(regDtoB);
        tokenOrgB = authB.accessToken();
        orgIdB = authB.organizationId();
    }

    @Test
    @DisplayName("1. Overview KPIs calculated accurately via pre-computed SQL aggregations")
    void shouldComputeOverviewMetricsAccurately() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequests").value(2))
                .andExpect(jsonPath("$.data.activeRequests").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.pendingApprovals").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.slaComplianceRate").value(greaterThanOrEqualTo(0.0)));
    }

    @Test
    @DisplayName("2. Breakdown by status and category returns accurate counts and percentages")
    void shouldProvideStatusAndCategoryBreakdowns() throws Exception {
        // Status breakdown
        mockMvc.perform(get("/api/v1/analytics/by-status")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").isNotEmpty())
                .andExpect(jsonPath("$.data[0].count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].percentage").value(greaterThan(0.0)));

        // Category metrics
        mockMvc.perform(get("/api/v1/analytics/by-category")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].categoryKey").value("HARDWARE"))
                .andExpect(jsonPath("$.data[0].totalRequests").value(2));
    }

    @Test
    @DisplayName("3. Department metrics and comprehensive executive report generation")
    void shouldProvideDepartmentMetricsAndExecutiveReport() throws Exception {
        // Department metrics list
        mockMvc.perform(get("/api/v1/analytics/by-department")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].departmentCode").value("ENG"))
                .andExpect(jsonPath("$.data[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data[0].totalRequests").value(2));

        // Department executive report for Engineering
        mockMvc.perform(get("/api/v1/analytics/department-report")
                        .param("departmentCode", "ENG")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentCode").value("ENG"))
                .andExpect(jsonPath("$.data.departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data.totalRequests").value(2))
                .andExpect(jsonPath("$.data.executiveSummary").value(containsString("Operational Report for Engineering")));
    }

    @Test
    @DisplayName("4. Strict multi-tenant isolation enforced on analytics queries")
    void shouldEnforceTenantIsolationOnAnalytics() throws Exception {
        // Tenant B inspects overview metrics -> Must see 0 requests, 0 pending approvals
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequests").value(0))
                .andExpect(jsonPath("$.data.activeRequests").value(0))
                .andExpect(jsonPath("$.data.pendingApprovals").value(0))
                .andExpect(jsonPath("$.data.openTasks").value(0));
    }

    @Test
    @DisplayName("5. Nexus AI executes generateDepartmentReport tool with pre-computed SQL metrics")
    void shouldAllowNexusAiToGenerateDepartmentReport() throws Exception {
        ChatRequestDto chatDto = new ChatRequestDto(null, "Generate department report for Engineering");

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.toolUsed").value("generateDepartmentReport"))
                .andExpect(jsonPath("$.data.reply").value(containsString("Engineering")))
                .andExpect(jsonPath("$.data.reply").value(containsString("Total requests logged: 2")));
    }
}

