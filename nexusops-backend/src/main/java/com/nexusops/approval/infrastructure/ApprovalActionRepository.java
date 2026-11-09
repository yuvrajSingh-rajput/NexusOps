package com.nexusops.approval.infrastructure;

import com.nexusops.approval.domain.ApprovalActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalActionEntity, UUID> {
    List<ApprovalActionEntity> findAllByApprovalInstanceIdOrderByCreatedAtAsc(UUID approvalInstanceId);
}

