package com.nexusops.knowledge.infrastructure;

import com.nexusops.knowledge.domain.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findByOrganizationIdAndDocumentVersionIdOrderByChunkIndexAsc(
            UUID organizationId, UUID documentVersionId);

    List<DocumentChunkEntity> findByOrganizationId(UUID organizationId);

    void deleteByOrganizationIdAndDocumentVersionId(UUID organizationId, UUID documentVersionId);

    @Query(value = """
            SELECT dc.id AS id,
                   dc.content AS content,
                   dc.page_number AS pageNumber,
                   dc.chunk_index AS chunkIndex,
                   d.title AS documentTitle,
                   d.id AS documentId,
                   (1.0 - (dc.embedding <=> cast(:embedding as vector))) AS similarityScore
            FROM document_chunks dc
            JOIN document_versions dv ON dc.document_version_id = dv.id
            JOIN documents d ON dv.document_id = d.id
            WHERE dc.organization_id = :organizationId
              AND dv.processing_status = 'INDEXED'
            ORDER BY dc.embedding <=> cast(:embedding as vector) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSimilarityProjection> searchSimilarChunksPostgres(
            @Param("organizationId") UUID organizationId,
            @Param("embedding") String embedding,
            @Param("limit") int limit);
}

