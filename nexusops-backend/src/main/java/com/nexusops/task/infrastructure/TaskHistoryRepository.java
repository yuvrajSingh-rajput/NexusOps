package com.nexusops.task.infrastructure;

import com.nexusops.task.domain.TaskHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistoryEntity, UUID> {
    List<TaskHistoryEntity> findAllByOrganizationIdAndTaskIdOrderByCreatedAtAsc(UUID organizationId, UUID taskId);
}

