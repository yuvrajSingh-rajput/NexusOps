package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowDefinitionService;
import com.nexusops.workflow.application.WorkflowEngineService;
import com.nexusops.workflow.application.dto.NodeInstanceResponseDto;
import com.nexusops.workflow.application.dto.WorkflowDefinitionResponseDto;
import com.nexusops.workflow.application.dto.WorkflowInstanceResponseDto;
import com.nexusops.workflow.application.dto.WorkflowVersionResponseDto;
import com.nexusops.workflow.domain.NodeType;
import com.nexusops.workflow.domain.WorkflowStatus;
import com.nexusops.workflow.domain.WorkflowVersionStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkflowEngineIntegrationTest {

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
    private WorkflowDefinitionService definitionService;

    @Autowired
    private WorkflowEngineService engineService;

    private AuthResponseDto tenantAdmin;
    private UserPrincipal adminPrincipal;
    private RequestCategoryResponseDto laptopCategory;

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto reg = new RegisterOrgRequestDto(
                "Acme Robotics " + randomSuffix,
                "acme-robotics-" + randomSuffix,
                "workflow-admin-" + randomSuffix + "@acme.com",
                "Password123!",
                "Workflow",
                "Admin"
        );
        tenantAdmin = authService.registerOrganization(reg);
        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ORG_ADMIN_MANAGE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("WORKFLOW_MANAGE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("APPROVAL_EXECUTE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("TASK_OPERATE"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("REQUEST_CREATE")
        );

        adminPrincipal = new UserPrincipal(
                tenantAdmin.userId(),
                tenantAdmin.organizationId(),
                tenantAdmin.userId(), // mock membership
                tenantAdmin.email(),
                authorities
        );

        departmentService.createDepartment(new CreateDepartmentDto("ENG", "Engineering", null, null));
        categoryService.seedDefaultCategoriesForTenant(tenantAdmin.organizationId());
        laptopCategory = categoryService.getActiveCategories().stream()
                .filter(c -> "DEVELOPER_LAPTOP".equals(c.code()))
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSeedAndPublishReferenceLaptopWorkflow() {
        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());

        List<WorkflowDefinitionResponseDto> defs = definitionService.getDefinitions(tenantAdmin.organizationId());
        assertThat(defs).isNotEmpty();

        WorkflowDefinitionResponseDto laptopDef = defs.stream()
                .filter(d -> "WF_DEVELOPER_LAPTOP".equals(d.code()))
                .findFirst()
                .orElseThrow();

        assertThat(laptopDef.name()).contains("Developer Laptop");
        assertThat(laptopDef.latestVersionNumber()).isEqualTo(1);
        assertThat(laptopDef.latestVersionStatus()).isEqualTo(WorkflowVersionStatus.PUBLISHED);

        WorkflowVersionResponseDto ver = definitionService.getVersion(
                tenantAdmin.organizationId(), laptopDef.id(), 1
        );
        assertThat(ver.nodes()).hasSize(8); // START, MGR_APPROVAL, COND_AMOUNT, FIN_APPROVAL, HEAD_APPROVAL, TASK_PROCUREMENT, END_COMPLETED, END_REJECTED
        assertThat(ver.transitions()).hasSize(11);
    }

    @Test
    void shouldExecuteWorkflowBranchingForLowAmountPurchase() {
        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());

        // 1. Submit laptop request with amount = $2,500 (<= $10,000 threshold)
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "MacBook Air M3 for QA",
                "Testing environment requisition",
                RequestPriority.MEDIUM,
                Map.of(
                        "deviceModel", "Apple MacBook Air 15 M3",
                        "ramGb", 16,
                        "storage", "512GB",
                        "estimatedAmount", 2500,
                        "urgencyReason", "Standard renewal"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        // 2. Verify workflow automatically started and paused at MGR_APPROVAL in WAITING state
        WorkflowInstanceResponseDto instance = engineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();

        assertThat(instance.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(instance.currentNodeKey()).isEqualTo("MGR_APPROVAL");

        // 3. Manager Approves
        WorkflowInstanceResponseDto resumed = engineService.resumeWorkflowWithSignal(
                tenantAdmin.organizationId(), instance.id(), "APPROVED", Map.of("approver", "manager@acme.com")
        );

        // Under $10,000, COND_AMOUNT routes directly to TASK_PROCUREMENT, bypassing Finance
        assertThat(resumed.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(resumed.currentNodeKey()).isEqualTo("TASK_PROCUREMENT");

        // Verify Node Instances history
        List<NodeInstanceResponseDto> nodes = engineService.getNodeInstances(tenantAdmin.organizationId(), instance.id());
        List<String> visitedKeys = nodes.stream().map(NodeInstanceResponseDto::nodeKey).toList();
        assertThat(visitedKeys).containsSubsequence("START", "MGR_APPROVAL", "COND_AMOUNT", "TASK_PROCUREMENT");

        // 4. Operator Completes IT Fulfillment Task
        WorkflowInstanceResponseDto completedWf = engineService.resumeWorkflowWithSignal(
                tenantAdmin.organizationId(), instance.id(), "COMPLETED", Map.of("serialNumber", "SN-998822")
        );

        assertThat(completedWf.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completedWf.completedAt()).isNotNull();
    }

    @Test
    void shouldRouteThroughFinanceApprovalForMediumAmount() {
        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());

        // Request with amount = $25,000 (> $10k, <= $50k)
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "High-performance GPU Workstation",
                "Deep learning model training",
                RequestPriority.HIGH,
                Map.of(
                        "deviceModel", "Custom GPU Workstation",
                        "ramGb", 128,
                        "storage", "4TB",
                        "estimatedAmount", 25000,
                        "urgencyReason", "Critical model training"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        WorkflowInstanceResponseDto instance = engineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();

        // Manager Approves
        WorkflowInstanceResponseDto afterMgr = engineService.resumeWorkflowWithSignal(
                tenantAdmin.organizationId(), instance.id(), "APPROVED", Map.of()
        );

        // Must route to FIN_APPROVAL
        assertThat(afterMgr.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(afterMgr.currentNodeKey()).isEqualTo("FIN_APPROVAL");

        // Finance Approves -> routes to TASK_PROCUREMENT
        WorkflowInstanceResponseDto afterFin = engineService.resumeWorkflowWithSignal(
                tenantAdmin.organizationId(), instance.id(), "APPROVED", Map.of()
        );
        assertThat(afterFin.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(afterFin.currentNodeKey()).isEqualTo("TASK_PROCUREMENT");
    }

    @Test
    void shouldTerminateWorkflowAsRejectedWhenManagerRejects() {
        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());

        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "Unjustified Upgrade Request",
                "Need 128GB RAM for spreadsheets",
                RequestPriority.LOW,
                Map.of(
                        "deviceModel", "ThinkPad P16",
                        "ramGb", 128,
                        "storage", "2TB",
                        "estimatedAmount", 8000,
                        "urgencyReason", "Personal preference"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        WorkflowInstanceResponseDto instance = engineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();

        // Manager Rejects
        WorkflowInstanceResponseDto afterMgr = engineService.resumeWorkflowWithSignal(
                tenantAdmin.organizationId(), instance.id(), "REJECTED", Map.of("reason", "Budget constraints")
        );

        // Must terminate immediately as REJECTED with no tasks created
        assertThat(afterMgr.status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(afterMgr.completedAt()).isNotNull();

        List<NodeInstanceResponseDto> nodes = engineService.getNodeInstances(tenantAdmin.organizationId(), instance.id());
        List<String> visitedKeys = nodes.stream().map(NodeInstanceResponseDto::nodeKey).toList();
        assertThat(visitedKeys).doesNotContain("TASK_PROCUREMENT");
    }

    @Test
    void shouldEnforceTenantIsolationOnWorkflows() throws Exception {
        definitionService.seedReferenceLaptopWorkflow(tenantAdmin.organizationId(), laptopCategory.id());

        // Create Tenant B
        RegisterOrgRequestDto regB = new RegisterOrgRequestDto(
                "Globex Corp " + UUID.randomUUID().toString().substring(0, 8),
                "globex-" + UUID.randomUUID().toString().substring(0, 8),
                "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@globex.com",
                "Password123!",
                "Globex",
                "Admin"
        );
        AuthResponseDto tenantB = authService.registerOrganization(regB);

        // Query definitions with Tenant B's JWT -> Tenant B should NOT see Tenant A's workflows
        mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + tenantB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
