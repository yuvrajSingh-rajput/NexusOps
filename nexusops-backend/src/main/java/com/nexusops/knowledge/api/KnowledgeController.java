package com.nexusops.knowledge.api;

import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.DocumentResponseDto;
import com.nexusops.knowledge.application.dto.DocumentVersionResponseDto;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import com.nexusops.knowledge.application.dto.SemanticSearchRequestDto;
import com.nexusops.knowledge.application.dto.UploadDocumentDto;
import com.nexusops.knowledge.domain.DocumentAccessScope;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponseDto>> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", defaultValue = "GENERAL") String category,
            @RequestParam(value = "accessScope", defaultValue = "ALL") DocumentAccessScope accessScope,
            @RequestParam(value = "targetScopeId", required = false) UUID targetScopeId,
            @RequestParam("file") MultipartFile file) {

        UploadDocumentDto dto = new UploadDocumentDto(title, description, category, accessScope, targetScopeId);
        DocumentResponseDto response = knowledgeService.uploadDocument(
                principal.getOrganizationId(), principal.getMembershipId(), dto, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<Page<DocumentResponseDto>>> listDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "category", required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<DocumentResponseDto> documents = knowledgeService.listDocuments(
                principal.getOrganizationId(), category, pageable);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<DocumentResponseDto>> getDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        DocumentResponseDto document = knowledgeService.getDocument(principal.getOrganizationId(), id);
        return ResponseEntity.ok(ApiResponse.success(document));
    }

    @GetMapping("/documents/{id}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersionResponseDto>>> getDocumentVersions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        List<DocumentVersionResponseDto> versions = knowledgeService.getDocumentVersions(principal.getOrganizationId(), id);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @GetMapping("/documents/{id}/versions/{versionNumber}/download")
    public ResponseEntity<InputStreamResource> downloadDocumentVersion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable int versionNumber) {

        InputStream stream = knowledgeService.downloadDocumentVersion(principal.getOrganizationId(), id, versionNumber);
        DocumentResponseDto doc = knowledgeService.getDocument(principal.getOrganizationId(), id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.latestFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

    @PostMapping("/documents/{id}/reindex")
    public ResponseEntity<ApiResponse<DocumentResponseDto>> reindexDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        DocumentResponseDto response = knowledgeService.reindexDocument(principal.getOrganizationId(), id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        knowledgeService.deleteDocument(principal.getOrganizationId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<SearchResultDto>>> semanticSearch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SemanticSearchRequestDto request) {

        List<SearchResultDto> results = knowledgeService.semanticSearch(
                principal.getOrganizationId(),
                request.query(),
                request.resolvedLimit(),
                request.resolvedMinScore()
        );
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
