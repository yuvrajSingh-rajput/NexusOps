package com.nexusops.platform.application;

import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.IdentityService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.EmployeeResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.UploadDocumentDto;
import com.nexusops.knowledge.domain.DocumentAccessScope;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.OrganizationService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.security.JwtTokenProvider;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import com.nexusops.workflow.application.WorkflowDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Commercial-grade Demo Data Seeder.
 * Bootstraps standard multi-tenant personas, departmental structure, reference workflows,
 * knowledge base RAG documents, and initial operational telemetry for Acme Corp and Globex Corp.
 */
@Component
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AuthService authService;
    private final IdentityService identityService;
    private final OrganizationService organizationService;
    private final DepartmentService departmentService;
    private final RequestCategoryService requestCategoryService;
    private final RequestService requestService;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final KnowledgeService knowledgeService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${nexusops.seed.demo-data:false}")
    private boolean seedDemoDataEnabled;

    public DemoDataSeeder(AuthService authService,
                          IdentityService identityService,
                          OrganizationService organizationService,
                          DepartmentService departmentService,
                          RequestCategoryService requestCategoryService,
                          RequestService requestService,
                          WorkflowDefinitionService workflowDefinitionService,
                          KnowledgeService knowledgeService,
                          JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.identityService = identityService;
        this.organizationService = organizationService;
        this.departmentService = departmentService;
        this.requestCategoryService = requestCategoryService;
        this.requestService = requestService;
        this.workflowDefinitionService = workflowDefinitionService;
        this.knowledgeService = knowledgeService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedDemoDataEnabled) {
            seedAll();
        }
    }

    public void seedAll() {
        if (organizationService.existsBySlug("acme-corp")) {
            log.info("Demo data already seeded for Acme Corp (slug: acme-corp); skipping.");
            return;
        }

        log.info("====================================================================");
        log.info("Initializing NexusOps Commercial Demo Environment...");
        log.info("====================================================================");

        try {
            seedAcmeCorporation();
            seedGlobexCorporation();
            log.info("====================================================================");
            log.info("NexusOps Commercial Demo Environment successfully provisioned!");
            log.info("====================================================================");
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void seedAcmeCorporation() {
        log.info("Provisioning Tenant 1: Acme Corporation (acme-corp)...");

        // 1. Register Organization & Admin (Eve Vance)
        RegisterOrgRequestDto eveDto = new RegisterOrgRequestDto(
                "Acme Corporation",
                "acme-corp",
                "eve@acme.com",
                "Password123!",
                "Eve",
                "Vance"
        );
        AuthResponseDto eveAuth = authService.registerOrganization(eveDto);
        UUID acmeOrgId = eveAuth.organizationId();
        UUID eveMembershipId = jwtTokenProvider.getMembershipId(eveAuth.accessToken());
        TenantContextHolder.setTenantId(acmeOrgId);

        // 2. Create Departments
        DepartmentResponseDto deptEng = departmentService.createDepartment(
                new CreateDepartmentDto("ENG", "Engineering", null, null)
        );
        DepartmentResponseDto deptFin = departmentService.createDepartment(
                new CreateDepartmentDto("FIN", "Finance & Accounting", null, null)
        );
        DepartmentResponseDto deptIt = departmentService.createDepartment(
                new CreateDepartmentDto("IT", "Information Technology & Procurement", null, null)
        );
        departmentService.createDepartment(
                new CreateDepartmentDto("HR", "People Operations", null, null)
        );

        // 3. Provision Personas with password 'Password123!'
        // Bob Martinez (Engineering Manager)
        EmployeeResponseDto bob = identityService.createEmployee(
                "bob@acme.com", "Password123!", "Bob", "Martinez",
                "Engineering Manager", deptEng.id(), eveMembershipId, Set.of("MANAGER")
        );
        departmentService.setDepartmentHead(deptEng.id(), bob.membershipId());

        // Alice Chen (Senior Software Engineer) - reports to Bob
        EmployeeResponseDto alice = identityService.createEmployee(
                "alice@acme.com", "Password123!", "Alice", "Chen",
                "Senior Software Engineer", deptEng.id(), bob.membershipId(), Set.of("EMPLOYEE")
        );

        // Carol Davis (Finance Controller)
        EmployeeResponseDto carol = identityService.createEmployee(
                "carol@acme.com", "Password123!", "Carol", "Davis",
                "Finance Controller", deptFin.id(), eveMembershipId, Set.of("FINANCE")
        );
        departmentService.setDepartmentHead(deptFin.id(), carol.membershipId());

        // Dave Wilson (Senior IT Specialist / Operator)
        EmployeeResponseDto dave = identityService.createEmployee(
                "dave@acme.com", "Password123!", "Dave", "Wilson",
                "Senior IT Specialist", deptIt.id(), eveMembershipId, Set.of("OPERATOR")
        );
        departmentService.setDepartmentHead(deptIt.id(), dave.membershipId());

        // 4. Seed Default Request Categories
        requestCategoryService.seedDefaultCategoriesForTenant(acmeOrgId);
        RequestCategoryResponseDto laptopCat = requestCategoryService.getCategoryByCode(acmeOrgId, "DEVELOPER_LAPTOP")
                .orElseThrow(() -> new IllegalStateException("DEVELOPER_LAPTOP category missing"));
        RequestCategoryResponseDto softwareCat = requestCategoryService.getCategoryByCode(acmeOrgId, "SOFTWARE_ACCESS")
                .orElseThrow(() -> new IllegalStateException("SOFTWARE_ACCESS category missing"));
        RequestCategoryResponseDto expenseCat = requestCategoryService.getCategoryByCode(acmeOrgId, "EXPENSE_REIMBURSEMENT")
                .orElseThrow(() -> new IllegalStateException("EXPENSE_REIMBURSEMENT category missing"));

        // 5. Seed & Publish Reference Laptop Workflow (v1)
        workflowDefinitionService.seedReferenceLaptopWorkflow(acmeOrgId, laptopCat.id());

        // 6. Seed Policy Document for Nexus AI (RAG)
        seedAcmeHardwarePolicy(acmeOrgId, eveMembershipId);

        // 7. Seed Initial Baseline Requests for Dashboard & Analytics
        seedInitialRequests(acmeOrgId, alice, bob, laptopCat, expenseCat);

        log.info("Acme Corporation successfully seeded with 5 personas, 4 departments, workflows, and policy knowledge.");
    }

    private void seedGlobexCorporation() {
        log.info("Provisioning Tenant 2: Globex Corporation (globex-corp) for multi-tenant isolation testing...");

        // 1. Register Homer Simpson (Globex Admin)
        RegisterOrgRequestDto homerDto = new RegisterOrgRequestDto(
                "Globex Corporation",
                "globex-corp",
                "homer@globex.com",
                "Password123!",
                "Homer",
                "Simpson"
        );
        AuthResponseDto homerAuth = authService.registerOrganization(homerDto);
        UUID globexOrgId = homerAuth.organizationId();
        UUID homerMembershipId = jwtTokenProvider.getMembershipId(homerAuth.accessToken());
        TenantContextHolder.setTenantId(globexOrgId);

        // 2. Department
        DepartmentResponseDto deptOps = departmentService.createDepartment(
                new CreateDepartmentDto("OPS", "Nuclear Facility Operations", null, null)
        );

        // 3. Employee Frank Grimes
        identityService.createEmployee(
                "frank@globex.com", "Password123!", "Frank", "Grimes",
                "Nuclear Safety Technician", deptOps.id(), homerMembershipId, Set.of("EMPLOYEE")
        );

        // 4. Default Categories
        requestCategoryService.seedDefaultCategoriesForTenant(globexOrgId);

        log.info("Globex Corporation successfully seeded (completely isolated tenant).");
    }

    private void seedAcmeHardwarePolicy(UUID organizationId, UUID membershipId) {
        String policyMarkdown = """
                # Acme Corporation - Global IT Hardware & Procurement Policy (2026)

                ## 1. Scope & Eligible Hardware
                All full-time employees are eligible for standard enterprise workstations.
                Engineering and Architecture staff are provisioned high-performance workstations:
                - Apple MacBook Pro 16" (M3 Max, 64GB/128GB Unified Memory)
                - Dell XPS 15 9530 (Intel Core i9, 64GB RAM, NVIDIA RTX 4070)
                - Framework Laptop 16 (Modular Ryzen 9 Edition)

                ## 2. Procurement Approval Thresholds & Governance
                All IT hardware purchases must be routed through the NexusOps automated workflow engine:
                - Tier 1 (Up to $10,000): Requires Direct Manager approval only. Upon approval, automatically routes to IT Procurement.
                - Tier 2 ($10,001 to $50,000): Requires Direct Manager approval followed by Finance Controller review.
                - Tier 3 (Exceeding $50,000): Requires Direct Manager, Finance Controller, and Department Head sign-off.

                ## 3. Refresh Cadence & Lifecycle
                Standard developer laptops operate on a 3-year hardware refresh cycle. Devices experiencing battery degradation or hardware faults can be submitted for expedited replacement with manager endorsement.

                ## 4. Fulfillment & Delivery
                Approved orders are fulfilled by the IT Procurement & Operations Team. Typical fulfillment SLA is 5 to 7 business days for standard configurations and 10 to 14 business days for custom high-RAM developer configurations.
                """;

        ByteArrayMultipartFile file = new ByteArrayMultipartFile(
                "file", "acme-it-procurement-policy-2026.md", "text/markdown",
                policyMarkdown.getBytes(StandardCharsets.UTF_8)
        );

        UploadDocumentDto dto = new UploadDocumentDto(
                "Acme Corporation - Global IT Hardware & Procurement Policy (2026)",
                "Official enterprise hardware equipment standards, approval tiers ($10k/$50k), and lifecycle replacement guidelines.",
                "POLICY",
                DocumentAccessScope.ALL,
                null
        );

        try {
            knowledgeService.uploadDocument(organizationId, membershipId, dto, file);
            log.info("Indexed Acme Hardware Policy into Knowledge Base vector store for RAG.");
        } catch (Exception e) {
            log.warn("Knowledge base seeding skipped or encountered warning: {}", e.getMessage());
        }
    }

    private void seedInitialRequests(UUID organizationId, EmployeeResponseDto alice, EmployeeResponseDto bob,
                                     RequestCategoryResponseDto laptopCat, RequestCategoryResponseDto expenseCat) {
        try {
            // Baseline Laptop Refresh Request by Alice (under $10k threshold)
            UserPrincipal alicePrincipal = new UserPrincipal(
                    alice.userId(), organizationId, alice.membershipId(), alice.email(),
                    List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("REQUEST_CREATE"))
            );
            CreateRequestDto lapReq = new CreateRequestDto(
                    laptopCat.id(),
                    alice.departmentId(),
                    "Standard Developer Laptop Hardware Refresh",
                    "Routine 3-year refresh to MacBook Pro 16 M3 Max (32GB RAM).",
                    RequestPriority.MEDIUM,
                    Map.of(
                            "deviceModel", "MacBook Pro 16 M3 Max",
                            "ramGb", 32,
                            "estimatedAmount", 3500,
                            "urgencyReason", "Scheduled 3-year engineering refresh"
                    ),
                    true
            );
            requestService.createRequest(lapReq, alicePrincipal);

            log.info("Seeded initial baseline requests for immediate operational analytics.");
        } catch (Exception e) {
            log.warn("Initial baseline requests seeding note: {}", e.getMessage());
        }
    }
}
