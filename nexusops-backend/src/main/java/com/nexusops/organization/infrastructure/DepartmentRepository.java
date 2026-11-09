package com.nexusops.organization.infrastructure;

import com.nexusops.organization.domain.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {

    @Query("SELECT d FROM DepartmentEntity d LEFT JOIN FETCH d.parentDepartment WHERE d.organizationId = :orgId")
    List<DepartmentEntity> findAllByOrganizationId(@Param("orgId") UUID orgId);

    @Query("SELECT d FROM DepartmentEntity d LEFT JOIN FETCH d.parentDepartment WHERE d.organizationId = :orgId AND d.id = :id")
    Optional<DepartmentEntity> findByOrganizationIdAndId(@Param("orgId") UUID orgId, @Param("id") UUID id);

    Optional<DepartmentEntity> findByOrganizationIdAndCode(UUID organizationId, String code);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
