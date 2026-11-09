package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.DocumentResponseDto;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import com.nexusops.knowledge.application.dto.SemanticSearchRequestDto;
import com.nexusops.knowledge.domain.DocumentProcessingStatus;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KnowledgeEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private KnowledgeService knowledgeService;

    private String tokenOrgA;
    private UUID orgIdA;

    private String tokenOrgB;
    private UUID orgIdB;

    @BeforeEach
    void setUp() {
        // Register Organization A
        String slugA = "acme-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoA = new RegisterOrgRequestDto(
                "Acme Corp", slugA, "alice." + slugA + "@acme.com", "Password123!", "Alice", "Admin");
        AuthResponseDto authA = authService.registerOrganization(regDtoA);
        tokenOrgA = authA.accessToken();
        orgIdA = authA.organizationId();

        // Register Organization B
        String slugB = "globex-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoB = new RegisterOrgRequestDto(
                "Globex", slugB, "bob." + slugB + "@globex.com", "Password123!", "Bob", "Admin");
        AuthResponseDto authB = authService.registerOrganization(regDtoB);
        tokenOrgB = authB.accessToken();
        orgIdB = authB.organizationId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldUploadAndIndexDocumentAndPerformSemanticSearch() throws Exception {
        String documentContent = """
                # NexusOps Employee Hardware & Laptop Policy (2026)
                
                ## Section 1: Developer Laptop Provisioning
                Engineering employees are entitled to standard high-performance laptops.
                The standard hardware tier is a 16-inch workstation with 32GB RAM and 1TB SSD.
                The maximum allowable budget for developer laptops is $3,500 without executive approval.
                
                ## Section 2: Replacement Cycle
                Hardware refresh occurs every 3 years. Early replacement requires VP approval.
                Damaged equipment must be reported immediately to the IT procurement desk.
                
                ## Section 3: Peripheral Allowance
                Employees are allocated an annual peripheral stipend of $500 for external monitors,
                ergonomic mechanical keyboards, and noise-cancelling headphones.
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "laptop-policy-2026.md", "text/markdown", documentContent.getBytes(StandardCharsets.UTF_8));

        // 1. Upload Document via REST
        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents/upload")
                        .file(file)
                        .param("title", "Engineering Hardware Policy 2026")
                        .param("description", "Standards for developer laptops and peripherals")
                        .param("category", "POLICY")
                        .param("accessScope", "ALL")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Engineering Hardware Policy 2026"))
                .andExpect(jsonPath("$.data.latestStatus").value("INDEXED"))
                .andExpect(jsonPath("$.data.latestFileName").value("laptop-policy-2026.md"))
                .andExpect(jsonPath("$.data.totalChunks").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn().getResponse().getContentAsString();

        String docIdStr = objectMapper.readTree(uploadResponse).path("data").path("id").asText();
        UUID documentId = UUID.fromString(docIdStr);

        // 2. Verify Document Detail Endpoint
        mockMvc.perform(get("/api/v1/knowledge/documents/{id}", documentId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.category").value("POLICY"));

        // 3. Verify Document Download Endpoint
        byte[] downloaded = mockMvc.perform(get("/api/v1/knowledge/documents/{id}/versions/1/download", documentId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(downloaded, StandardCharsets.UTF_8)).isEqualTo(documentContent);

        // 4. Perform Semantic Search on Relevant Query
        SemanticSearchRequestDto searchDto = new SemanticSearchRequestDto("developer laptop budget allowance", 5, 0.40);
        String searchResponse = mockMvc.perform(post("/api/v1/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchDto))
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].documentTitle").value("Engineering Hardware Policy 2026"))
                .andExpect(jsonPath("$.data[0].similarityScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.40)))
                .andReturn().getResponse().getContentAsString();

        assertThat(searchResponse).contains("developer laptops");
    }

    @Test
    void shouldEnforceStrictCrossTenantKnowledgeIsolation() throws Exception {
        String sensitiveOrgAContent = """
                CONFIDENTIAL: Acme Corp Strategic Acquisitions Plan
                Acme Corp plans to acquire internal logistics suppliers in Q4.
                Strict confidentiality required across all departments.
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "acme-strategy.txt", "text/plain", sensitiveOrgAContent.getBytes(StandardCharsets.UTF_8));

        // Upload in Org A
        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents/upload")
                        .file(file)
                        .param("title", "Acme Confidential Strategy")
                        .param("category", "SECURITY")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docIdStr = objectMapper.readTree(uploadResponse).path("data").path("id").asText();
        UUID docId = UUID.fromString(docIdStr);

        // Org B Attempts to Access Org A's Document -> Expect 404 Not Found
        mockMvc.perform(get("/api/v1/knowledge/documents/{id}", docId)
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isNotFound());

        // Org B Attempts to Download Org A's Document -> Expect 404 Not Found
        mockMvc.perform(get("/api/v1/knowledge/documents/{id}/versions/1/download", docId)
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isNotFound());

        // Org B Searches For Org A's Keywords -> Expect ZERO Results
        SemanticSearchRequestDto searchDto = new SemanticSearchRequestDto("Acme strategic acquisitions", 5, 0.20);
        mockMvc.perform(post("/api/v1/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchDto))
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldSupportDocumentReindexingAndDeletion() throws Exception {
        String content = "Standard operating procedure for emergency server maintenance.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "sop.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        String uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge/documents/upload")
                        .file(file)
                        .param("title", "Server Maintenance SOP")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docIdStr = objectMapper.readTree(uploadResponse).path("data").path("id").asText();
        UUID docId = UUID.fromString(docIdStr);

        // Reindex Document
        mockMvc.perform(post("/api/v1/knowledge/documents/{id}/reindex", docId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestStatus").value("INDEXED"));

        // Delete Document
        mockMvc.perform(delete("/api/v1/knowledge/documents/{id}", docId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk());

        // Verify Subsequent Query Returns 404
        mockMvc.perform(get("/api/v1/knowledge/documents/{id}", docId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isNotFound());
    }
}
