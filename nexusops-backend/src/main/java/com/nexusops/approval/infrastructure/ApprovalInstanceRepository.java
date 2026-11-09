package com.nexusops.approval.infrastructure;

import com.nexusops.approval.domain.ApprovalInstanceEntity;
import com.nexusops.approval.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstanceEntity, UUID> {

    Optional<ApprovalInstanceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ApprovalInstanceEntity> findAllByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);

    Optional<ApprovalInstanceEntity> findByOrganizationIdAndNodeInstanceId(UUID organizationId, UUID nodeInstanceId);

    @Query("SELECT a FROM ApprovalInstanceEntity a WHERE a.organizationId = :orgId AND a.status = :status AND " +
           "(a.assignedMembershipId = :membershipId OR (a.assignedRoleId IS NOT NULL AND a.assignedRoleId IN :roleIds)) " +
           "ORDER BY a.createdAt DESC")
    List<ApprovalInstanceEntity> findPendingForApprover(
            @Param("orgId") UUID orgId,
            @Param("status") ApprovalStatus status,
            @Param("membershipId") UUID membershipId,
            @Param("roleIds") Collection<UUID> roleIds
    );

    @Query("SELECT a FROM ApprovalInstanceEntity a WHERE a.organizationId = :orgId AND a.status = :status AND " +
           "a.assignedMembershipId = :membershipId ORDER BY a.createdAt DESC")
    List<ApprovalInstanceEntity> findPendingForDirectMembership(
            @Param("orgId") UUID orgId,
            @Param("status") ApprovalStatus status,
            @Param("membershipId") UUID membershipId
    );
}

