# NexusOps – Security Architecture & Tenant Isolation Model

**Document Version:** 1.0.0  
**Status:** Approved for Architecture & Design  
**Author:** NexusOps Architecture Team  

---

## 1. Security Philosophy & Defense-in-Depth Architecture

Security in NexusOps is built on the principle of **Defense in Depth**: no single layer is relied upon for tenant isolation or authorization. If any layer were to suffer a regression, succeeding layers prevent data leakage or unauthorized mutation.

```
                    [Inbound HTTP Request]
                              │
                              ▼
Layer 1: Gateway ─────── Rate Limiting & Strict CORS
                              │
                              ▼
Layer 2: Auth Filter ─── JWT Validation -> User ID & Organization ID Extracted
                              │
                              ▼
Layer 3: Thread Local ── TenantContextHolder Populated (Server-Derived Only)
                              │
                              ▼
Layer 4: Method Sec ──── Spring Security @PreAuthorize + Custom Permission Evaluators
                              │
                              ▼
Layer 5: Service Level ── Explicit Resource Ownership & Scope Checks (Anti-IDOR)
                              │
                              ▼
Layer 6: Database FK ─── Composite Foreign Keys (organization_id, id)
                              │
                              ▼
Layer 7: Database RLS ── PostgreSQL Row-Level Security (tenant_isolation_policy)
                              │
                              ▼
Layer 8: Audit Log ───── Append-Only Tamper-Evident Audit Record
```

---

## 2. Authentication & Token Lifecycle

### 2.1 Dual-Token Architecture
NexusOps uses a combination of short-lived Access JWTs and stateful rotating Refresh Tokens:

| Token Type | Storage | Expiration | Purpose |
| :--- | :--- | :--- | :--- |
| **Access Token** | In-Memory (React state / Authorization header) | 15 Minutes | Stateless API authentication. Contains `sub` (User ID), `org_id` (Active Tenant ID), `roles`, and `permissions`. |
| **Refresh Token** | `HttpOnly`, `SameSite=Strict`, `Secure` Cookie | 7 Days | Stateful session token used to obtain a new Access Token. Stored as SHA-256 hash in Redis. |

### 2.2 Refresh Token Rotation & Reuse Detection
To protect against token theft:
1. Every time `/api/v1/auth/refresh` is called, the old refresh token is invalidated, and a brand new refresh token is issued.
2. If an invalidated refresh token is ever presented (indicating token theft by an attacker):
   - The security subsystem immediately triggers **Session Revocation**.
   - All active refresh tokens and sessions for that user are purged from Redis.
   - A critical security audit alert is generated.

---

## 3. Multi-Tenant Isolation Guarantees

### 3.1 Strict Prohibition on Client-Supplied Tenant Identifiers
- **The Rule:** The backend **never** accepts `organization_id` or `tenant_id` from client query parameters, request bodies, headers, or AI tool arguments for authorization.
- **The Implementation:** `organization_id` is extracted strictly from the cryptographically verified JWT in `JwtAuthenticationFilter` and stored in `TenantContextHolder`:
  ```java
  public class TenantContextHolder {
      private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

      public static void setTenantId(UUID tenantId) { CURRENT_TENANT.set(tenantId); }
      public static UUID getTenantId() { return CURRENT_TENANT.get(); }
      public static void clear() { CURRENT_TENANT.remove(); }
  }
  ```

### 3.2 Tenant-Aware Composite Foreign Keys
Cross-tenant foreign key injection is made physically impossible at the PostgreSQL engine level using composite foreign keys:
```sql
-- Prevents referencing an approval belonging to another organization
ALTER TABLE approval_actions
ADD CONSTRAINT fk_approval_actions_tenant_instance
FOREIGN KEY (organization_id, approval_instance_id)
REFERENCES approval_instances (organization_id, id);
```

### 3.3 PostgreSQL Row-Level Security (RLS)
Every tenant-owned table is registered with an RLS policy:
```sql
ALTER TABLE requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON requests
    AS RESTRICTIVE
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
```
Even if a developer accidentally wrote `SELECT * FROM requests WHERE id = :id` without adding `organization_id`, PostgreSQL filters out any rows that do not match `app.current_tenant_id`.

---

## 4. Authorization & IDOR (Insecure Direct Object Reference) Protection

### 4.1 Granular RBAC Permissions
Rather than hardcoding logic to role names, controllers enforce granular permissions:

```java
@PreAuthorize("hasAuthority('REQUEST_CREATE')")
@PostMapping("/requests")
public ApiResponse<RequestResponseDto> createRequest(@Valid @RequestBody CreateRequestDto dto) {
    ...
}
```

### 4.2 Resource-Level Scope Checks (Preventing IDOR)
Having permission `REQUEST_VIEW` does not mean an employee can view any employee's request. Method security uses custom permission expressions:

```java
@PreAuthorize("hasAuthority('REQUEST_VIEW_ALL') or @requestSecurity.isRequesterOrManager(#requestId)")
@GetMapping("/requests/{requestId}")
public ApiResponse<RequestDetailDto> getRequest(@PathVariable UUID requestId) {
    ...
}
```

Every database lookup includes the tenant ID:
```java
@Query("SELECT r FROM RequestEntity r WHERE r.organizationId = :orgId AND r.id = :requestId")
Optional<RequestEntity> findByTenantAndId(@Param("orgId") UUID orgId, @Param("requestId") UUID requestId);
```

---

## 5. File Upload & MinIO Security

1. **Size Limits:** Strict maximum size of 25MB enforced at the servlet filter level.
2. **Magic-Byte Content Sniffing:** File extensions are considered untrusted. MIME type is determined by inspecting the file header using Apache Tika.
3. **Whitelisted Formats:** Only `application/pdf`, `image/png`, `image/jpeg`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (DOCX), and `text/plain` are accepted.
4. **Isolated Storage Paths:** Files are stored under UUID keys:
   `s3://nexusops-documents/{organizationId}/attachments/{randomUUID}`
   Files are never served directly via public URLs; they are downloaded via authenticated, pre-signed MinIO URLs with a 5-minute validity window.

---

## 6. Audit Integrity & Anti-Tampering

1. **Append-Only Enforcement:** The PostgreSQL database user used by the application service is granted `INSERT` and `SELECT` privileges on `audit_logs`, but **no `UPDATE` or `DELETE`** privileges.
2. **Correlation ID Tracking:** Every incoming HTTP request is assigned a `X-Correlation-Id` (UUID v4) via an MDC filter. The correlation ID is propagated across all log statements, Kafka messages, outbox rows, and audit records.
3. **Sensitive Data Sanitization:** An audit sanitizer intercepts JSON before/after snapshots and strips sensitive keys (`password`, `password_hash`, `jwt`, `token`, `secret`, `ssn`, `credit_card`) prior to serialization.

