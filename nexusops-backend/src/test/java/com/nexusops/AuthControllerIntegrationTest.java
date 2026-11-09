package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.identity.api.AuthController;
import com.nexusops.identity.application.dto.LoginRequestDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.identity.application.dto.InviteEmployeeDto;
import jakarta.servlet.http.Cookie;
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

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully register organization, return access token and set HttpOnly refresh cookie")
    void testRegisterOrganization_Success() throws Exception {
        RegisterOrgRequestDto dto = new RegisterOrgRequestDto(
                "Acme Corp",
                "acme-" + UUID.randomUUID().toString().substring(0, 8),
                "admin-" + UUID.randomUUID().toString().substring(0, 6) + "@acme.com",
                "SecurePassword123!",
                "Alice",
                "Admin"
        );

        mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.roles", hasItem("ORG_ADMIN")))
                .andExpect(jsonPath("$.data.permissions", hasItem("ORG_ADMIN_MANAGE")))
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
                .andExpect(cookie().httpOnly(AuthController.REFRESH_COOKIE_NAME, true));
    }

    @Test
    @DisplayName("Should reject duplicate organization registration with same slug (409 Conflict)")
    void testRegisterOrganization_DuplicateSlug_Fails() throws Exception {
        String slug = "unique-slug-" + UUID.randomUUID().toString().substring(0, 6);
        RegisterOrgRequestDto dto1 = new RegisterOrgRequestDto(
                "First Org",
                slug,
                "admin1@" + slug + ".com",
                "Password123!",
                "First",
                "Admin"
        );

        mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        RegisterOrgRequestDto dto2 = new RegisterOrgRequestDto(
                "Second Org",
                slug,
                "admin2@" + slug + ".com",
                "Password123!",
                "Second",
                "Admin"
        );

        mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_SLUG_EXISTS"));
    }

    @Test
    @DisplayName("Should successfully login, reject bad credentials, and rotate refresh token")
    void testLogin_And_RefreshTokenRotation() throws Exception {
        String email = "login-test-" + UUID.randomUUID().toString().substring(0, 6) + "@nexusops.com";
        String password = "CorrectPassword123!";

        RegisterOrgRequestDto regDto = new RegisterOrgRequestDto(
                "Login Test Org",
                "login-org-" + UUID.randomUUID().toString().substring(0, 6),
                email,
                password,
                "John",
                "Doe"
        );

        mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regDto)))
                .andExpect(status().isCreated());

        // Bad password -> 401
        LoginRequestDto badLogin = new LoginRequestDto(email, "WrongPassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());

        // Correct password -> 200 with refresh cookie
        LoginRequestDto goodLogin = new LoginRequestDto(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);

        // Refresh token rotation -> succeeds and returns new cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
                .andReturn();

        // Replay of old refresh token -> Rejected (Token reuse protection)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cross-tenant isolation test: Tenant B cannot access Tenant A's departments or employees")
    void testCrossTenantIsolation() throws Exception {
        // Register Tenant A
        RegisterOrgRequestDto orgA = new RegisterOrgRequestDto(
                "Company A", "comp-a-" + UUID.randomUUID().toString().substring(0, 6),
                "alice@company-a.com", "PasswordA123!", "Alice", "Admin"
        );
        MvcResult resA = mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgA)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenA = extractAccessToken(resA);

        // Register Tenant B
        RegisterOrgRequestDto orgB = new RegisterOrgRequestDto(
                "Company B", "comp-b-" + UUID.randomUUID().toString().substring(0, 6),
                "bob@company-b.com", "PasswordB123!", "Bob", "Admin"
        );
        MvcResult resB = mockMvc.perform(post("/api/v1/auth/register-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgB)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenB = extractAccessToken(resB);

        // Alice creates a department in Tenant A
        CreateDepartmentDto deptDto = new CreateDepartmentDto("ENG", "Software Engineering", null, null);
        MvcResult deptResult = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("ENG"))
                .andReturn();

        String deptIdA = objectMapper.readTree(deptResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 1. Bob attempts direct IDOR query on Alice's department -> 404 Not Found
        mockMvc.perform(get("/api/v1/departments/" + deptIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // 2. Bob lists departments -> sees 0 departments (Tenant A's ENG is completely hidden)
        mockMvc.perform(get("/api/v1/departments")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // 3. Alice lists departments -> sees 1 department ("ENG")
        mockMvc.perform(get("/api/v1/departments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("ENG"));

        // 4. Alice invites Charlie to Tenant A
        InviteEmployeeDto inviteDto = new InviteEmployeeDto(
                "charlie@company-a.com", "Charlie", "Engineer", "Staff Dev",
                UUID.fromString(deptIdA), null, Set.of("EMPLOYEE")
        );
        mockMvc.perform(post("/api/v1/memberships/invite")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteDto)))
                .andExpect(status().isCreated());

        // 5. Bob queries memberships in Tenant B -> sees only Bob (never Alice or Charlie)
        mockMvc.perform(get("/api/v1/memberships")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value("bob@company-b.com"));
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
