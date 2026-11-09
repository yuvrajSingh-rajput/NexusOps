package com.nexusops.request.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_comments")
public class RequestCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "author_membership_id", nullable = false)
    private UUID authorMembershipId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommentVisibility visibility = CommentVisibility.REQUESTER_VISIBLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RequestCommentEntity() {
    }

    public RequestCommentEntity(UUID organizationId, UUID requestId, UUID authorMembershipId,
                                String content, CommentVisibility visibility) {
        this.organizationId = organizationId;
        this.requestId = requestId;
        this.authorMembershipId = authorMembershipId;
        this.content = content;
        this.visibility = visibility != null ? visibility : CommentVisibility.REQUESTER_VISIBLE;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getAuthorMembershipId() {
        return authorMembershipId;
    }

    public void setAuthorMembershipId(UUID authorMembershipId) {
        this.authorMembershipId = authorMembershipId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(CommentVisibility visibility) {
        this.visibility = visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
