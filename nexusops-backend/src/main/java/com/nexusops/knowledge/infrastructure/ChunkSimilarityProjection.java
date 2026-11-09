package com.nexusops.knowledge.infrastructure;

import java.util.UUID;

public interface ChunkSimilarityProjection {
    UUID getId();
    String getContent();
    Integer getPageNumber();
    Integer getChunkIndex();
    String getDocumentTitle();
    UUID getDocumentId();
    Double getSimilarityScore();
}

