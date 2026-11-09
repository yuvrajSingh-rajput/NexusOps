package com.nexusops.request.infrastructure;

import com.nexusops.request.domain.RequestEntity;
import com.nexusops.request.domain.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<RequestEntity, UUID> {

    Optional<RequestEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    Optional<RequestEntity> findByOrganizationIdAndRequestNumber(UUID organizationId, String requestNumber);

    Page<RequestEntity> findAllByOrganizationIdAndRequesterMembershipId(UUID organizationId, UUID requesterMembershipId, Pageable pageable);

    Page<RequestEntity> findAllByOrganizationIdAndRequesterMembershipIdAndStatus(UUID organizationId, UUID requesterMembershipId, RequestStatus status, Pageable pageable);

    Page<RequestEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<RequestEntity> findAllByOrganizationIdAndStatus(UUID organizationId, RequestStatus status, Pageable pageable);

    Page<RequestEntity> findAllByOrganizationIdAndCategoryId(UUID organizationId, UUID categoryId, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, RequestStatus status);
}

