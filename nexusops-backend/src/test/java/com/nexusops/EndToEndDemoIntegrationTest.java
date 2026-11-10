package com.nexusops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.ai.application.dto.ChatRequestDto;
import com.nexusops.approval.domain.ApprovalActionType;
import com.nexusops.identity.application.dto.LoginRequestDto;
import com.nexusops.platform.application.DemoDataSeeder;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.events.EventPublisher;
import com.nexusops.shared.events.OutboxRelayPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Master End-to-End Acceptance Test for NexusOps.
 *
 * Implements the master evaluation journey defined in AGENTS.md Section 2:
 * 1. Alice (Employee) logs in and submits a laptop requisition ($25,000 developer workstation rig).
 * 2. Bob (Manager) logs in and approves -> advances past condition threshold (> $10k).
 * 3. Carol (Finance) logs in and approves -> all approvals complete.
 * 4. Dave (IT Operator) claims and completes the procurement task -> Request is COMPLETED.
 * 5. Eve (Org Admin) logs in and inspects the immutable audit trail and operational analytics.
 * 6. Nexus AI answers policy questions with citations and generates department reports.
 * 7. Multi-tenant isolation is strictly verified (Globex employee cannot access Acme assets).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndDemoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private OutboxRelayPublisher outboxRelayPublisher;

    @MockBean
    private EventPublisher eventPublisher;

    private static boolean seeded = false;

    @BeforeEach
    void setUp() {
        if (!seeded) {
            demoDataSeeder.seedAll();
            seeded = true;
        }
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequestDto loginDto = new LoginRequestDto(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    @Test
    @Order(1)
    @DisplayName("Acceptance Demo: Full Lifecycle Journey (Employee -> Manager -> Finance -> Operator -> Admin)")
    void executeFullCommercialLifecycleAcceptanceDemo() throws Exception {
        // =========================================================================
        // Step 1: Alice Chen (Employee) logs in & inspects available categories
        // =========================================================================
        String aliceToken = loginAndGetToken("alice@acme.com", "Password123!");
        assertNotNull(aliceToken);

        MvcResult catResult = mockMvc.perform(get("/api/v1/request-categories")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andReturn();

        JsonNode catList = objectMapper.readTree(catResult.getResponse().getContentAsString()).path("data");
        UUID laptopCatId = null;
        for (JsonNode cat : catList) {
            if ("DEVELOPER_LAPTOP".equals(cat.path("code").asText())) {
                laptopCatId = UUID.fromString(cat.path("id").asText());
                break;
            }
        }
        assertNotNull(laptopCatId, "DEVELOPER_LAPTOP category must exist");

        // Step 2: Alice submits a Laptop Requisition ($25,000 Workstation Rig)
        Map<String, Object> requestPayload = Map.of(
                "deviceModel", "MacBook Pro 16 M3 Max",
                "ramGb", 128,
                "estimatedAmount", 25000,
                "urgencyReason", "Production LLM fine-tuning and distributed systems simulation lab"
        );

        Map<String, Object> createReqDto = Map.of(
                "categoryId", laptopCatId,
                "title", "High-Performance Developer AI Workstation Rig",
                "description", "Dual external display workstation and 128GB unified RAM for high-throughput local inference.",
                "priority", RequestPriority.URGENT.name(),
                "payload", requestPayload,
                "autoSubmit", true
        );

        MvcResult reqResult = mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.requestNumber").value(matchesPattern("REQ-\\d+")))
                .andReturn();

        JsonNode reqData = objectMapper.readTree(reqResult.getResponse().getContentAsString()).path("data");
        UUID createdRequestId = UUID.fromString(reqData.path("id").asText());
        String requestNumber = reqData.path("requestNumber").asText();
        assertNotNull(createdRequestId);

        // =========================================================================
        // Step 3: Bob Martinez (Manager) logs in & approves Tier 1
        // =========================================================================
        String bobToken = loginAndGetToken("bob@acme.com", "Password123!");

        MvcResult bobPendingResult = mockMvc.perform(get("/api/v1/approvals/my-pending")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        JsonNode bobApprovals = objectMapper.readTree(bobPendingResult.getResponse().getContentAsString()).path("data");
        UUID bobApprovalId = null;
        for (JsonNode app : bobApprovals) {
            if (createdRequestId.toString().equals(app.path("requestId").asText())) {
                bobApprovalId = UUID.fromString(app.path("id").asText());
                break;
            }
        }
        assertNotNull(bobApprovalId, "Bob must have pending approval for Alice's request");

        // Bob executes APPROVE
        Map<String, Object> bobDecision = Map.of(
                "action", ApprovalActionType.APPROVE.name(),
                "comments", "Engineering leadership approval granted. Critical for Q4 delivery velocity."
        );

        mockMvc.perform(post("/api/v1/approvals/" + bobApprovalId + "/action")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // =========================================================================
        // Step 4: Carol Davis (Finance Controller) logs in & approves Tier 2 ($25k > $10k threshold)
        // =========================================================================
        String carolToken = loginAndGetToken("carol@acme.com", "Password123!");

        MvcResult carolPendingResult = mockMvc.perform(get("/api/v1/approvals/my-pending")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        JsonNode carolApprovals = objectMapper.readTree(carolPendingResult.getResponse().getContentAsString()).path("data");
        UUID carolApprovalId = null;
        for (JsonNode app : carolApprovals) {
            if (createdRequestId.toString().equals(app.path("requestId").asText())) {
                carolApprovalId = UUID.fromString(app.path("id").asText());
                break;
            }
        }
        assertNotNull(carolApprovalId, "Carol (Finance) must have pending approval for $25k requisition");

        // Carol executes APPROVE
        Map<String, Object> carolDecision = Map.of(
                "action", ApprovalActionType.APPROVE.name(),
                "comments", "CapEx budget line ENG-CAPEX-2026 authorized. Passed finance review."
        );

        mockMvc.perform(post("/api/v1/approvals/" + carolApprovalId + "/action")
                        .header("Authorization", "Bearer " + carolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(carolDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // =========================================================================
        // Step 5: Dave Wilson (Senior IT Operator) claims & completes Task
        // =========================================================================
        String daveToken = loginAndGetToken("dave@acme.com", "Password123!");

        MvcResult queueResult = mockMvc.perform(get("/api/v1/tasks/queue")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        JsonNode queueTasks = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data");
        UUID itTaskId = null;
        for (JsonNode t : queueTasks) {
            if (createdRequestId.toString().equals(t.path("requestId").asText())) {
                itTaskId = UUID.fromString(t.path("id").asText());
                break;
            }
        }
        assertNotNull(itTaskId, "IT Procurement task must be generated upon approval completion");

        // Dave claims task
        mockMvc.perform(post("/api/v1/tasks/" + itTaskId + "/claim")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // Dave completes task with fulfillment details
        Map<String, Object> completeTaskDto = Map.of(
                "resolutionNotes", "Workstation provisioned with enterprise MDM and delivered to Alice Chen. Asset Tag: #ACME-8849-MBP."
        );

        mockMvc.perform(post("/api/v1/tasks/" + itTaskId + "/complete")
                        .header("Authorization", "Bearer " + daveToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeTaskDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // =========================================================================
        // Step 6: Verify Request is fully COMPLETED
        // =========================================================================
        mockMvc.perform(get("/api/v1/requests/" + createdRequestId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // =========================================================================
        // Step 7: Eve Vance (Org Admin) audits the trail & analytics
        // =========================================================================
        String eveToken = loginAndGetToken("eve@acme.com", "Password123!");

        // Trigger outbox relay publisher to guarantee pending transactional outbox events are dispatched
        outboxRelayPublisher.relayPendingEvents();

        // 7a. Audit trail verification
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data.content[*].eventType", hasItems(
                        "REQUEST_SUBMITTED",
                        "APPROVAL_COMPLETED",
                        "TASK_ASSIGNED",
                        "TASK_COMPLETED",
                        "WORKFLOW_COMPLETED"
                )));

        // 7b. Operational analytics verification
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.completedRequests", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.slaComplianceRate", greaterThanOrEqualTo(0.0)));
    }

    @Test
    @Order(2)
    @DisplayName("Acceptance Demo: Nexus AI Assistant (Policy RAG with Citations, Tool Execution & Reports)")
    void executeNexusAiAcceptanceCapabilities() throws Exception {
        String aliceToken = loginAndGetToken("alice@acme.com", "Password123!");

        // 1. Policy Question RAG with Citations
        ChatRequestDto policyQuery = new ChatRequestDto(null, "What are the laptop purchase approval tiers and policies?");

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyQuery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.citations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.citations[0].documentTitle", containsString("Acme Corporation - Global IT Hardware & Procurement Policy")));

        // 2. Executive Department Report Generation
        String eveToken = loginAndGetToken("eve@acme.com", "Password123!");
        ChatRequestDto reportQuery = new ChatRequestDto(null, "Generate department report for ENG");

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + eveToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportQuery)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply", containsString("Report for Engineering")))
                .andExpect(jsonPath("$.data.toolUsed").value("generateDepartmentReport"));
    }

    @Test
    @Order(3)
    @DisplayName("Acceptance Demo: Tenant Boundary Isolation (Globex Employee Blocked from Acme Assets)")
    void verifyCrossTenantIsolationStrictness() throws Exception {
        // Frank Grimes logs in to Globex Corp
        String frankToken = loginAndGetToken("frank@globex.com", "Password123!");
        String aliceToken = loginAndGetToken("alice@acme.com", "Password123!");

        // 1. Get Alice's Request ID from Acme via /my
        MvcResult acmeReqsResult = mockMvc.perform(get("/api/v1/requests/my")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode acmeItems = objectMapper.readTree(acmeReqsResult.getResponse().getContentAsString()).path("data").path("content");
        assertTrue(acmeItems.size() > 0, "Acme must have requests");
        String acmeRequestId = acmeItems.get(0).path("id").asText();

        // 2. Frank (Globex) attempts to fetch Acme's request by ID -> 404 Not Found (IDOR protection)
        mockMvc.perform(get("/api/v1/requests/" + acmeRequestId)
                        .header("Authorization", "Bearer " + frankToken))
                .andExpect(status().isNotFound());

        // 3. Frank (Globex) views his request queue -> receives 0 Acme requests
        mockMvc.perform(get("/api/v1/requests/my")
                        .header("Authorization", "Bearer " + frankToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 4. Frank views pending approvals -> receives 0 Acme approvals
        mockMvc.perform(get("/api/v1/approvals/my-pending")
                        .header("Authorization", "Bearer " + frankToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 5. Frank queries AI for Acme confidential policy -> RAG returns 0 Acme citations
        ChatRequestDto leakAttempt = new ChatRequestDto(null, "What are the Acme hardware procurement approval tiers?");
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + frankToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leakAttempt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations", hasSize(0)));
    }

    @Test
    @Order(4)
    @DisplayName("Acceptance Demo: Comprehensive Multi-Persona Request & Dashboard Permissions (Operator, Finance, Employee)")
    void verifyMultiPersonaPermissions() throws Exception {
        // 1. Dave Wilson (Operator) login & permissions verification
        String daveToken = loginAndGetToken("dave@acme.com", "Password123!");

        // Dave can view 'My Requests' without 403
        mockMvc.perform(get("/api/v1/requests/my")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Dave can view 'All Requests' without 403
        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        // Dave can view dashboard overview without 403
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + daveToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequests", greaterThanOrEqualTo(1)));

        // 2. Carol Davis (Finance Controller) login & permissions verification
        String carolToken = loginAndGetToken("carol@acme.com", "Password123!");

        // Carol can view 'My Requests' without 403
        mockMvc.perform(get("/api/v1/requests/my")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Carol can view 'All Requests' without 403
        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Alice Chen (Employee) dashboard overview verification
        String aliceToken = loginAndGetToken("alice@acme.com", "Password123!");

        // Alice can view dashboard overview without 403
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
