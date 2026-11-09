package com.nexusops.task.infrastructure;

import com.nexusops.task.domain.TaskCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskCommentEntity, UUID> {
    List<TaskCommentEntity> findAllByOrganizationIdAndTaskIdOrderByCreatedAtAsc(UUID organizationId, UUID taskId);
}

