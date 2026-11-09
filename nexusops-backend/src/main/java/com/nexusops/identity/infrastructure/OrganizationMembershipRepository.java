package com.nexusops.identity.infrastructure;

import com.nexusops.identity.domain.OrganizationMembershipEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, UUID> {

    @Query("SELECT m FROM OrganizationMembershipEntity m JOIN FETCH m.user LEFT JOIN FETCH m.roles WHERE m.organizationId = :orgId AND m.user.id = :userId")
    Optional<OrganizationMembershipEntity> findByOrganizationIdAndUserId(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    @Query("SELECT m FROM OrganizationMembershipEntity m JOIN FETCH m.user LEFT JOIN FETCH m.roles WHERE m.organizationId = :orgId AND m.id = :id")
    Optional<OrganizationMembershipEntity> findByOrganizationIdAndId(@Param("orgId") UUID orgId, @Param("id") UUID id);

    @Query("SELECT m FROM OrganizationMembershipEntity m JOIN FETCH m.user WHERE m.user.id = :userId")
    List<OrganizationMembershipEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query(value = "SELECT m FROM OrganizationMembershipEntity m JOIN FETCH m.user WHERE m.organizationId = :orgId",
           countQuery = "SELECT COUNT(m) FROM OrganizationMembershipEntity m WHERE m.organizationId = :orgId")
    Page<OrganizationMembershipEntity> findAllByOrganizationId(@Param("orgId") UUID orgId, Pageable pageable);
}
