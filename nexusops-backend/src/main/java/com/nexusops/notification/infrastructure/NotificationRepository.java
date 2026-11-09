package com.nexusops.notification.infrastructure;

import com.nexusops.notification.domain.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByOrganizationIdAndRecipientMembershipIdOrderByCreatedAtDesc(
            UUID organizationId, UUID recipientMembershipId, Pageable pageable);

    long countByOrganizationIdAndRecipientMembershipIdAndReadFalse(
            UUID organizationId, UUID recipientMembershipId);

    Optional<NotificationEntity> findByOrganizationIdAndRecipientMembershipIdAndId(
            UUID organizationId, UUID recipientMembershipId, UUID id);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.organizationId = :orgId AND n.recipientMembershipId = :membershipId")
    void markAllAsRead(@Param("orgId") UUID orgId, @Param("membershipId") UUID membershipId);
}

