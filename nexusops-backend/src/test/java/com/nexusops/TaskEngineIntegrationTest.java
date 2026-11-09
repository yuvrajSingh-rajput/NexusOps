package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.approval.application.ApprovalService;
import com.nexusops.approval.application.dto.ApprovalDecisionDto;
import com.nexusops.approval.application.dto.ApprovalInstanceResponseDto;
import com.nexusops.approval.domain.ApprovalActionType;
import com.nexusops.identity.application.AuthService;
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
import com.nexusops.task.application.TaskService;
import com.nexusops.task.application.dto.*;
import com.nexusops.task.domain.TaskEntity;
import com.nexusops.task.domain.TaskPriority;
import com.nexusops.task.domain.TaskStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TaskEngineIntegrationTest {

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
    private WorkflowEngineService workflowEngineService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private TaskService taskService;

    private AuthResponseDto tenantAdmin;
    private UserPrincipal adminPrincipal;
    private RequestCategoryResponseDto laptopCategory;

    @BeforeEach
    void setUp() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto reg = new RegisterOrgRequestDto(
                "Task Logistics Corp " + randomSuffix,
                "task-corp-" + randomSuffix,
                "operator-admin-" + randomSuffix + "@taskcorp.com",
                "Password123!",
                "Fulfillment",
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
                tenantAdmin.userId(),
                tenantAdmin.email(),
                authorities
        );

        departmentService.createDepartment(new CreateDepartmentDto("OPS", "IT Operations", null, null));
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
    void shouldCompleteFullEndToEndProcurementFulfillmentJourney() throws Exception {
        // 1. Employee submits a laptop request ($3,200 < $10,000 threshold)
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "MacBook Pro 16 M3 for Senior Operator",
                "Standard workstation fulfillment",
                RequestPriority.HIGH,
                Map.of(
                        "deviceModel", "Apple MacBook Pro 16",
                        "ramGb", 32,
                        "storage", "1TB",
                        "estimatedAmount", 3200,
                        "urgencyReason", "Hardware procurement"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);
        assertThat(request.status()).isEqualTo(RequestStatus.IN_REVIEW);

        // 2. Manager receives and approves the pending approval
        List<ApprovalInstanceResponseDto> approvals = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        );
        assertThat(approvals).hasSize(1);
        ApprovalInstanceResponseDto approval = approvals.get(0);

        approvalService.decideApproval(
                tenantAdmin.organizationId(),
                approval.id(),
                new ApprovalDecisionDto(ApprovalActionType.APPROVE, "Manager budget approved", null),
                adminPrincipal
        );

        // 3. Workflow condition routes directly to TASK_PROCUREMENT and fires TaskRequestedEvent!
        WorkflowInstanceResponseDto wf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(wf.status()).isEqualTo(WorkflowStatus.WAITING);
        assertThat(wf.currentNodeKey()).isEqualTo("TASK_PROCUREMENT");

        // 4. Verify Task was automatically created for this request
        List<TaskResponseDto> requestTasks = taskService.getTasksForRequest(tenantAdmin.organizationId(), request.id());
        assertThat(requestTasks).hasSize(1);
        TaskResponseDto task = requestTasks.get(0);
        assertThat(task.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.taskNumber()).startsWith("TASK-");
        assertThat(task.title()).isEqualTo("IT Procurement & Provisioning");

        // 5. Operator inspects unassigned queue via REST API
        mockMvc.perform(get("/api/v1/tasks/queue")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(task.id().toString()));

        // 6. Operator claims the task
        mockMvc.perform(post("/api/v1/tasks/" + task.id() + "/claim")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.assigneeMembershipId").isNotEmpty());

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // 7. Operator adds progress comment
        AddTaskCommentDto commentDto = new AddTaskCommentDto("Ordered from Apple Store. Tracking unit created.");
        mockMvc.perform(post("/api/v1/tasks/" + task.id() + "/comments")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Ordered from Apple Store. Tracking unit created."));

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // 8. Operator completes the task with operational fulfillment data
        CompleteTaskDto completeDto = new CompleteTaskDto(
                Map.of(
                        "serialNumber", "SN-APEX-998822",
                        "carrier", "FedEx",
                        "trackingNumber", "789123456789",
                        "deliveredDate", "2026-11-12"
                ),
                "Hardware provisioned and imaging complete"
        );

        mockMvc.perform(post("/api/v1/tasks/" + task.id() + "/complete")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outputData.serialNumber").value("SN-APEX-998822"));

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // 9. Verify Workflow Engine received completion signal, advanced through END_COMPLETED, and finished
        WorkflowInstanceResponseDto completedWf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(completedWf.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completedWf.currentNodeKey()).isEqualTo("END_COMPLETED");

        // 10. Verify Request is formally marked COMPLETED!
        RequestResponseDto completedReq = requestService.getRequest(tenantAdmin.organizationId(), request.id());
        assertThat(completedReq.status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completedReq.completedAt()).isNotNull();
    }

    @Test
    void shouldNeverCreateTasksWhenRequestIsRejected() throws Exception {
        // 1. Submit request
        CreateRequestDto reqDto = new CreateRequestDto(
                laptopCategory.id(),
                null,
                "Unauthorized Gaming Rig",
                "Non-compliant requisition",
                RequestPriority.LOW,
                Map.of(
                        "deviceModel", "Alienware M18",
                        "ramGb", 64,
                        "storage", "4TB",
                        "estimatedAmount", 4500,
                        "urgencyReason", "Personal use"
                ),
                true
        );
        RequestResponseDto request = requestService.createRequest(reqDto, adminPrincipal);

        ApprovalInstanceResponseDto approval = approvalService.getApprovalsForRequest(
                tenantAdmin.organizationId(), request.id()
        ).get(0);

        // 2. Reject approval
        approvalService.decideApproval(
                tenantAdmin.organizationId(),
                approval.id(),
                new ApprovalDecisionDto(ApprovalActionType.REJECT, "Hardware rejected by policy", null),
                adminPrincipal
        );

        // 3. Verify workflow is terminated as REJECTED
        WorkflowInstanceResponseDto wf = workflowEngineService.getWorkflowInstanceForRequest(
                tenantAdmin.organizationId(), request.id()
        ).orElseThrow();
        assertThat(wf.status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(wf.currentNodeKey()).isEqualTo("END_REJECTED");

        // 4. Verify ZERO tasks exist for this rejected request!
        List<TaskResponseDto> tasks = taskService.getTasksForRequest(tenantAdmin.organizationId(), request.id());
        assertThat(tasks).isEmpty();
    }

    @Test
    void shouldUpdateTaskStatusAndRecordAuditHistory() throws Exception {
        // Create a standalone or workflow task
        TaskEntity entity = taskService.createWorkflowTask(
                tenantAdmin.organizationId(),
                "Server Room AC Maintenance",
                "HVAC periodic check",
                null, null, null, null,
                adminPrincipal.getMembershipId(),
                TaskPriority.HIGH,
                Map.of("facility", "Building C"),
                null
        );

        // Operator updates status to BLOCKED
        UpdateTaskStatusDto blockDto = new UpdateTaskStatusDto(TaskStatus.BLOCKED, "Waiting for replacement filters");
        mockMvc.perform(patch("/api/v1/tasks/" + entity.getId() + "/status")
                        .header("Authorization", "Bearer " + tenantAdmin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        TenantContextHolder.setTenantId(tenantAdmin.organizationId());

        // Verify history recorded the transition
        TaskResponseDto details = taskService.getTaskById(tenantAdmin.organizationId(), entity.getId());
        assertThat(details.history()).isNotEmpty();
        assertThat(details.history().stream().anyMatch(h -> "STATUS_CHANGED".equals(h.action()))).isTrue();
    }

    @Test
    void shouldEnforceCrossTenantTaskIsolation() throws Exception {
        // 1. Tenant A has a task
        TaskEntity taskA = taskService.createWorkflowTask(
                tenantAdmin.organizationId(),
                "Tenant A Sensitive Security Task",
                "Internal audit task",
                null, null, null, null,
                adminPrincipal.getMembershipId(),
                TaskPriority.HIGH,
                Map.of(), null
        );

        // 2. Register Tenant B
        RegisterOrgRequestDto regB = new RegisterOrgRequestDto(
                "Gamma Security",
                "gamma-corp-" + UUID.randomUUID().toString().substring(0, 8),
                "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@gamma.com",
                "Password123!",
                "Gamma",
                "Admin"
        );
        AuthResponseDto tenantB = authService.registerOrganization(regB);

        // 3. Tenant B tries to read Tenant A's task -> 404 Not Found
        mockMvc.perform(get("/api/v1/tasks/" + taskA.getId())
                        .header("Authorization", "Bearer " + tenantB.accessToken()))
                .andExpect(status().isNotFound());

        // 4. Tenant B queue query returns 0 tasks from Tenant A
        mockMvc.perform(get("/api/v1/tasks/queue")
                        .header("Authorization", "Bearer " + tenantB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
