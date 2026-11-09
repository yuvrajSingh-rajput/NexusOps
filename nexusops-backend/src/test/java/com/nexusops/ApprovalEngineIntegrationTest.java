package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.approval.application.ApprovalService;
import com.nexusops.approval.application.dto.ApprovalDecisionDto;
import com.nexusops.approval.application.dto.ApprovalInstanceResponseDto;
import com.nexusops.approval.domain.ApprovalActionType;
import com.nexusops.approval.domain.ApprovalStatus;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.IdentityService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.request.domain.RequestStatus;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowDefinitionService;
import com.nexusops.workflow.application.WorkflowEngineService;
import com.nexusops.workflow.application.dto.WorkflowInstanceResponseDto;
import com.nexusops.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApprovalEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private RequestCategoryService categoryService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private WorkflowDefinitionService definitionService;

    @Autowired
    private WorkflowEngineService workflowEngineService;

    @Autowired
    private ApprovalService approvalService;

    private AuthResponseDto tenantAdmin;
    private UserPrincipal adminPrincipal;
    private RequestCategoryResponseDto laptopCategory;

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto reg = new RegisterOrgRequestDto(
                "Apex Systems " + randomSuffix,
                "apex-systems-" + randomSuffix,
                "approval-admin-" + randomSuffix + "@apex.com",
                "Password123!",
                "Approval",
                "Admin"
        );
        tenantAdmin = authService.registerOrganization(reg);
        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ORG_ADMIN_MANAGE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("WORKFLOW_MANAGE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("APPROVAL_EXECUTE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("REQUEST_CREATE")
        );

        adminPrincipal = new UserPrincipal(
                tenantAdmin.userId(),
                tenantAdmin.organizationId(),
                tenantAdmin.userId(),
                tenantAdmin.email(),
                authorities
        );

        departmentService.createDepartment(new CreateDepartmentDto("ENG", "Engineering", null, null));
        categoryService.seedDefaultCategoriesForTenant(tenantAdmin.organizationId());
        laptopCategory = categoryService.getActiveCategories().stream()
                .filter(c -> "DEVELOPER_LAPTOP".equals(c.code()))
                .findFirst()
                .orElseThrow();

        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateApprovalAndAdvanceWorkflowOnApprove() throws Exception {
        // 1. Submit a laptop request
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "MacBook Pro 16 for Senior Architect",
                "Hardware refresh",
                RequestPriority.HIGH,
                Map.of(
                        "deviceModel", "Apple MacBook Pro 16 M3 Max",
                        "ramGb", 32,
                        "storage", "1TB",
                        "estimatedAmount", 3500,
                        "urgencyReason", "Production architecture work"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        // 2. Verify Approval instance was created automatically by workflow engine via ApprovalRequestedEvent
        List<ApprovalInstanceResponseDto> approvals = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        );
        assertThat(approvals).hasSize(1);
        ApprovalInstanceResponseDto approval = approvals.get(0);
        assertThat(approval.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.requestTitle()).isEqualTo("MacBook Pro 16 for Senior Architect");

        // 3. Admin decides to approve via REST API
        ApprovalDecisionDto decision = new ApprovalDecisionDto(
                ApprovalActionType.APPROVE,
                "Approved - standard equipment for senior engineering",
                null
        );

        mockMvc.perform(post("/api/v1/approvals/" + approval.id() + "/action")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.actions[0].action").value("APPROVE"))
                .andExpect(jsonPath("$.data.actions[0].comments").value("Approved - standard equipment for senior engineering"));

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // 4. Verify workflow automatically resumed and moved to TASK_PROCUREMENT (since $3,500 <= $10,000 threshold)
        WorkflowInstanceResponseDto wf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(wf.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(wf.currentNodeKey()).isEqualTo("TASK_PROCUREMENT");
    }

    @Test
    void shouldTerminateWorkflowAndSetRequestRejectedOnReject() throws Exception {
        // 1. Submit a laptop request
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "High-End Gaming Laptop",
                "Non-compliant hardware request",
                RequestPriority.LOW,
                Map.of(
                        "deviceModel", "Alienware M18",
                        "ramGb", 64,
                        "storage", "4TB",
                        "estimatedAmount", 4500,
                        "urgencyReason", "Personal preference"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        List<ApprovalInstanceResponseDto> approvals = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        );
        assertThat(approvals).hasSize(1);
        ApprovalInstanceResponseDto approval = approvals.get(0);

        // 2. Reject the approval
        ApprovalDecisionDto decision = new ApprovalDecisionDto(
                ApprovalActionType.REJECT,
                "Policy violation: non-standard gaming machine",
                null
        );

        mockMvc.perform(post("/api/v1/approvals/" + approval.id() + "/action")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // 3. Verify workflow is terminated as REJECTED with ZERO tasks
        WorkflowInstanceResponseDto wf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(wf.status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(wf.currentNodeKey()).isEqualTo("END_REJECTED");

        // 4. Verify request status is REJECTED
        RequestResponseDto updatedRequest = requestService.getRequest(tenantAdmin.organizationId(), request.id());
        assertThat(updatedRequest.status()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void shouldRouteToFinanceRoleApprovalForAmountExceedingThreshold() throws Exception {
        // 1. Submit request with amount $15,000 (> $10,000 threshold)
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "AI Workstation Laptop Cluster",
                "ML development unit",
                RequestPriority.URGENT,
                Map.of(
                        "deviceModel", "Custom GPU Mobile Rig",
                        "ramGb", 128,
                        "storage", "8TB",
                        "estimatedAmount", 15000,
                        "urgencyReason", "LLM fine-tuning cluster"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        // First approval: Manager
        List<ApprovalInstanceResponseDto> initialApprovals = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        );
        ApprovalInstanceResponseDto managerApproval = initialApprovals.get(0);

        // Manager Approves
        approvalService.decideApproval(
                tenantAdmin.organizationId(),
                managerApproval.id(),
                new ApprovalDecisionDto(ApprovalActionType.APPROVE, "Manager OK", null),
                adminPrincipal
        );

        // Workflow condition checks amount > 10,000 and routes to FIN_APPROVAL
        WorkflowInstanceResponseDto wf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(wf.currentNodeKey()).isEqualTo("FIN_APPROVAL");

        // Verify second approval instance created for role FINANCE
        List<ApprovalInstanceResponseDto> allApprovals = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        );
        assertThat(allApprovals).hasSize(2);
        ApprovalInstanceResponseDto financeApproval = allApprovals.stream()
                .filter(a -> a.status() == ApprovalStatus.PENDING)
                .findFirst()
                .orElseThrow();
        assertThat(financeApproval.approverRoleName()).isEqualTo("Role Queue");
    }

    @Test
    void shouldEnforceOptimisticLockingOnConcurrentDecisions() throws Exception {
        // 1. Create a request and approval
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "Concurrent Test Laptop",
                "Testing race conditions",
                RequestPriority.MEDIUM,
                Map.of(
                        "deviceModel", "MacBook Air",
                        "ramGb", 16,
                        "storage", "512GB",
                        "estimatedAmount", 1200,
                        "urgencyReason", "Concurrent test execution"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        ApprovalInstanceResponseDto approval = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        ).get(0);

        // 2. First decision succeeds
        ApprovalDecisionDto decision1 = new ApprovalDecisionDto(ApprovalActionType.APPROVE, "First approver", null);
        approvalService.decideApproval(tenantAdmin.organizationId(), approval.id(), decision1, adminPrincipal);

        // 3. Second decision on already decided approval throws 409 Conflict
        ApprovalDecisionDto decision2 = new ApprovalDecisionDto(ApprovalActionType.REJECT, "Second approver", null);
        mockMvc.perform(post("/api/v1/approvals/" + approval.id() + "/action")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldEnforceCrossTenantIsolation() throws Exception {
        // 1. Tenant A has an approval
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "Tenant A Confidential Requisition",
                "High security hardware",
                RequestPriority.HIGH,
                Map.of(
                        "deviceModel", "SecureBook",
                        "ramGb", 32,
                        "storage", "1TB",
                        "estimatedAmount", 2000,
                        "urgencyReason", "Secure tenant testing"
                ),
                true
        );
        RequestResponseDto requestA = requestService.createRequest(reqDto, adminPrincipal);
        ApprovalInstanceResponseDto approvalA = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), requestA.id()
        ).get(0);

        // 2. Register Tenant B
        RegisterOrgRequestDto regB = new RegisterOrgRequestDto(
                "Beta Corp",
                "beta-corp-" + UUID.randomUUID().toString().substring(0, 8),
                "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@beta.com",
                "Password123!",
                "Beta",
                "Admin"
        );
        AuthResponseDto tenantB = authService.registerOrganization(regB);

        // 3. Tenant B attempts to fetch Tenant A's approval -> 404 Not Found (tenant-scoped isolation)
        mockMvc.perform(get("/api/v1/approvals/" + approvalA.id())
                        .header("Authorization", "Bearer " + tenantB.accessToken()))
                .andExpect(status().isNotFound());

        // 4. Tenant B checks pending approvals -> Tenant A's approval is NOT returned
        mockMvc.perform(get("/api/v1/approvals/pending")
                        .header("Authorization", "Bearer " + tenantB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
