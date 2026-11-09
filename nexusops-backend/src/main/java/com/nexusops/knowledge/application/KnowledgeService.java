package com.nexusops.knowledge.application;

import com.nexusops.knowledge.application.dto.DocumentChunkDto;
import com.nexusops.knowledge.application.dto.DocumentResponseDto;
import com.nexusops.knowledge.application.dto.DocumentVersionResponseDto;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import com.nexusops.knowledge.application.dto.UploadDocumentDto;
import com.nexusops.knowledge.domain.DocumentAccessScope;
import com.nexusops.knowledge.domain.DocumentChunkEntity;
import com.nexusops.knowledge.domain.DocumentEntity;
import com.nexusops.knowledge.domain.DocumentProcessingStatus;
import com.nexusops.knowledge.domain.DocumentVersionEntity;
import com.nexusops.knowledge.infrastructure.ChunkSimilarityProjection;
import com.nexusops.knowledge.infrastructure.DocumentChunkRepository;
import com.nexusops.knowledge.infrastructure.DocumentRepository;
import com.nexusops.knowledge.infrastructure.DocumentVersionRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final StorageService storageService;
    private final TextExtractionService textExtractionService;
    private final DocumentChunkerService documentChunkerService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final DataSource dataSource;
    private volatile Boolean isPostgres;

    public KnowledgeService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentChunkRepository documentChunkRepository,
            StorageService storageService,
            TextExtractionService textExtractionService,
            DocumentChunkerService documentChunkerService,
            VectorEmbeddingService vectorEmbeddingService,
            DataSource dataSource) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.storageService = storageService;
        this.textExtractionService = textExtractionService;
        this.documentChunkerService = documentChunkerService;
        this.vectorEmbeddingService = vectorEmbeddingService;
        this.dataSource = dataSource;
    }

    private boolean isPostgreSql() {
        if (isPostgres == null) {
            try (var conn = dataSource.getConnection()) {
                String name = conn.getMetaData().getDatabaseProductName();
                isPostgres = name != null && name.toLowerCase().contains("postgres");
            } catch (Exception e) {
                isPostgres = false;
            }
        }
        return isPostgres;
    }

    /**
     * Upload a new document or create a new version of an existing document.
     */
    public DocumentResponseDto uploadDocument(UUID organizationId, UUID membershipId,
                                              UploadDocumentDto dto, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long fileSize = file.getSize();

        // Calculate SHA-256 checksum
        String checksum;
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            checksum = HexFormat.of().formatHex(digest.digest(fileBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate document checksum", e);
        }

        // Create Document Record
        DocumentEntity document = new DocumentEntity(
                organizationId,
                dto.title(),
                dto.description(),
                dto.category(),
                dto.accessScope() != null ? dto.accessScope() : DocumentAccessScope.ALL,
                dto.targetScopeId(),
                membershipId
        );
        document = documentRepository.save(document);

        // Determine Version Number
        int versionNumber = 1;
        String storageKey = String.format("%s/knowledge/%s/v%d/%s",
                organizationId, document.getId(), versionNumber, originalFileName);

        // Store Binary to Storage Service (MinIO or Local)
        try (InputStream is = file.getInputStream()) {
            storageService.store(storageKey, is, fileSize, contentType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store document in storage service: " + storageKey, e);
        }

        // Create Version Record
        DocumentVersionEntity version = new DocumentVersionEntity(
                organizationId,
                document,
                versionNumber,
                storageKey,
                originalFileName,
                fileSize,
                contentType,
                checksum
        );
        version = documentVersionRepository.save(version);
        document.addVersion(version);

        // Process, Chunk and Embed Immediately
        processDocumentVersion(version, fileBytes);

        return toDto(document, version);
    }

    /**
     * Ingest, chunk, embed, and store document vectors.
     */
    public void processDocumentVersion(DocumentVersionEntity version, byte[] fileBytes) {
        try {
            version.setProcessingStatus(DocumentProcessingStatus.PROCESSING);
            documentVersionRepository.save(version);

            // 1. Text Extraction
            List<TextExtractionService.ExtractedPage> pages;
            try (InputStream is = (fileBytes != null)
                    ? new java.io.ByteArrayInputStream(fileBytes)
                    : storageService.load(version.getStorageKey())) {
                pages = textExtractionService.extractText(is, version.getFileName(), version.getMimeType());
            }

            // 2. Sliding-Window Chunking
            List<DocumentChunkerService.RawChunk> rawChunks = documentChunkerService.chunkPages(pages);

            // 3. Clear Existing Chunks if any
            documentChunkRepository.deleteByOrganizationIdAndDocumentVersionId(
                    version.getOrganizationId(), version.getId());

            // 4. Generate Embeddings & Save Chunks
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();
            for (DocumentChunkerService.RawChunk raw : rawChunks) {
                float[] embedding = vectorEmbeddingService.generateEmbedding(raw.content());
                String formattedVector = vectorEmbeddingService.formatVector(embedding);

                DocumentChunkEntity chunkEntity = new DocumentChunkEntity(
                        version.getOrganizationId(),
                        version,
                        raw.chunkIndex(),
                        raw.content(),
                        raw.pageNumber(),
                        raw.tokenCount(),
                        formattedVector
                );
                chunkEntities.add(chunkEntity);
            }

            documentChunkRepository.saveAll(chunkEntities);

            // 5. Update Version Status
            version.setChunkCount(chunkEntities.size());
            version.setProcessingStatus(DocumentProcessingStatus.INDEXED);
            version.setProcessedAt(Instant.now());
            version.setErrorMessage(null);
            documentVersionRepository.save(version);

            log.info("Successfully indexed document version {} with {} chunks",
                    version.getId(), chunkEntities.size());

        } catch (Exception e) {
            log.error("Failed to process document version {}: {}", version.getId(), e.getMessage(), e);
            version.setProcessingStatus(DocumentProcessingStatus.FAILED);
            version.setErrorMessage(e.getMessage());
            documentVersionRepository.save(version);
        }
    }

    /**
     * Retrieve paginated documents for the tenant.
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponseDto> listDocuments(UUID organizationId, String category, Pageable pageable) {
        Page<DocumentEntity> page;
        if (category != null && !category.isBlank()) {
            page = documentRepository.findByOrganizationIdAndCategory(organizationId, category, pageable);
        } else {
            page = documentRepository.findByOrganizationId(organizationId, pageable);
        }

        return page.map(doc -> {
            Optional<DocumentVersionEntity> latest = documentVersionRepository
                    .findTopByOrganizationIdAndDocumentIdOrderByVersionNumberDesc(organizationId, doc.getId());
            return toDto(doc, latest.orElse(null));
        });
    }

    /**
     * Get single document details.
     */
    @Transactional(readOnly = true)
    public DocumentResponseDto getDocument(UUID organizationId, UUID documentId) {
        DocumentEntity document = documentRepository.findByOrganizationIdAndId(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        DocumentVersionEntity latest = documentVersionRepository
                .findTopByOrganizationIdAndDocumentIdOrderByVersionNumberDesc(organizationId, documentId)
                .orElse(null);

        return toDto(document, latest);
    }

    /**
     * Get versions for a document.
     */
    @Transactional(readOnly = true)
    public List<DocumentVersionResponseDto> getDocumentVersions(UUID organizationId, UUID documentId) {
        documentRepository.findByOrganizationIdAndId(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        return documentVersionRepository.findByOrganizationIdAndDocumentId(organizationId, documentId).stream()
                .map(this::toVersionDto)
                .toList();
    }

    /**
     * Download binary stream of a specific version.
     */
    @Transactional(readOnly = true)
    public InputStream downloadDocumentVersion(UUID organizationId, UUID documentId, int versionNumber) {
        DocumentVersionEntity version = documentVersionRepository
                .findByOrganizationIdAndDocumentIdAndVersionNumber(organizationId, documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Document version not found"));

        return storageService.load(version.getStorageKey());
    }

    /**
     * Re-index an existing document version.
     */
    public DocumentResponseDto reindexDocument(UUID organizationId, UUID documentId) {
        DocumentEntity document = documentRepository.findByOrganizationIdAndId(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        DocumentVersionEntity version = documentVersionRepository
                .findTopByOrganizationIdAndDocumentIdOrderByVersionNumberDesc(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("No version found for document: " + documentId));

        processDocumentVersion(version, null);
        return toDto(document, version);
    }

    /**
     * Delete document and all associated versions and chunks.
     */
    public void deleteDocument(UUID organizationId, UUID documentId) {
        DocumentEntity document = documentRepository.findByOrganizationIdAndId(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        List<DocumentVersionEntity> versions = documentVersionRepository
                .findByOrganizationIdAndDocumentId(organizationId, documentId);

        for (DocumentVersionEntity v : versions) {
            documentChunkRepository.deleteByOrganizationIdAndDocumentVersionId(organizationId, v.getId());
            storageService.delete(v.getStorageKey());
        }

        documentRepository.deleteByOrganizationIdAndId(organizationId, documentId);
        log.info("Deleted document {} and all its versions/chunks for tenant {}", documentId, organizationId);
    }

    /**
     * Perform tenant-scoped semantic similarity search across indexed document chunks.
     */
    @Transactional(readOnly = true)
    public List<SearchResultDto> semanticSearch(UUID organizationId, String query, int limit, double minScore) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        float[] queryVec = vectorEmbeddingService.generateEmbedding(query);
        String formattedVector = vectorEmbeddingService.formatVector(queryVec);

        if (isPostgreSql()) {
            try {
                // Attempt PostgreSQL pgvector cosine similarity search
                List<ChunkSimilarityProjection> projections = documentChunkRepository
                        .searchSimilarChunksPostgres(organizationId, formattedVector, limit);

                if (projections != null && !projections.isEmpty()) {
                    return projections.stream()
                            .filter(p -> p.getSimilarityScore() != null && p.getSimilarityScore() >= minScore)
                            .map(p -> new SearchResultDto(
                                    p.getId(),
                                    p.getDocumentId(),
                                    p.getDocumentTitle(),
                                    p.getPageNumber(),
                                    p.getChunkIndex(),
                                    Math.round(p.getSimilarityScore() * 1000.0) / 1000.0,
                                    p.getContent(),
                                    formatCitation(p.getDocumentTitle(), p.getPageNumber(), p.getChunkIndex())
                            ))
                            .toList();
                }
            } catch (Exception e) {
                log.warn("pgvector query failed in PostgreSQL environment: {}", e.getMessage());
            }
        }

        // In-Memory Cosine Similarity Fallback (Used during H2 unit testing and environments without pgvector)
        List<DocumentChunkEntity> allTenantChunks = documentChunkRepository.findByOrganizationId(organizationId);
        return allTenantChunks.stream()
                .filter(chunk -> chunk.getDocumentVersion() != null
                        && chunk.getDocumentVersion().getProcessingStatus() == DocumentProcessingStatus.INDEXED)
                .map(chunk -> {
                    float[] chunkVec = vectorEmbeddingService.parseVector(chunk.getEmbedding());
                    double score = vectorEmbeddingService.cosineSimilarity(queryVec, chunkVec);
                    String docTitle = (chunk.getDocumentVersion().getDocument() != null)
                            ? chunk.getDocumentVersion().getDocument().getTitle()
                            : "Document";
                    UUID docId = (chunk.getDocumentVersion().getDocument() != null)
                            ? chunk.getDocumentVersion().getDocument().getId()
                            : null;
                    return new SearchResultDto(
                            chunk.getId(),
                            docId,
                            docTitle,
                            chunk.getPageNumber(),
                            chunk.getChunkIndex(),
                            Math.round(score * 1000.0) / 1000.0,
                            chunk.getContent(),
                            formatCitation(docTitle, chunk.getPageNumber(), chunk.getChunkIndex())
                    );
                })
                .filter(r -> r.similarityScore() >= minScore)
                .sorted(Comparator.comparingDouble(SearchResultDto::similarityScore).reversed())
                .limit(limit)
                .toList();
    }

    private String formatCitation(String docTitle, Integer pageNumber, int chunkIndex) {
        if (pageNumber != null && pageNumber > 0) {
            return String.format("%s (Page %d)", docTitle, pageNumber);
        }
        return String.format("%s (Section %d)", docTitle, chunkIndex + 1);
    }

    private DocumentResponseDto toDto(DocumentEntity doc, DocumentVersionEntity latestVersion) {
        int latestVersionNum = (latestVersion != null) ? latestVersion.getVersionNumber() : 1;
        DocumentProcessingStatus status = (latestVersion != null)
                ? latestVersion.getProcessingStatus()
                : DocumentProcessingStatus.UPLOADED;
        String fileName = (latestVersion != null) ? latestVersion.getFileName() : "";
        long fileSize = (latestVersion != null) ? latestVersion.getFileSizeBytes() : 0L;
        int chunkCount = (latestVersion != null) ? latestVersion.getChunkCount() : 0;

        return new DocumentResponseDto(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getCategory(),
                doc.getAccessScope(),
                doc.getTargetScopeId(),
                doc.getCreatedByMembershipId(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                latestVersionNum,
                status,
                fileName,
                fileSize,
                chunkCount
        );
    }

    private DocumentVersionResponseDto toVersionDto(DocumentVersionEntity v) {
        return new DocumentVersionResponseDto(
                v.getId(),
                v.getDocument().getId(),
                v.getVersionNumber(),
                v.getFileName(),
                v.getFileSizeBytes(),
                v.getMimeType(),
                v.getProcessingStatus(),
                v.getErrorMessage(),
                v.getChunkCount(),
                v.getCreatedAt(),
                v.getProcessedAt()
        );
    }
}

