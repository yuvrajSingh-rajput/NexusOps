package com.nexusops.knowledge.application.dto;

import com.nexusops.knowledge.domain.DocumentAccessScope;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UploadDocumentDto(
        @NotBlank(message = "Document title is required")
        String title,
        String description,
        String category,
        DocumentAccessScope accessScope,
        UUID targetScopeId
) {}

