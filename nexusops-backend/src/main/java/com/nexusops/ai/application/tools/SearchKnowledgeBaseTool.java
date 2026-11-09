package com.nexusops.ai.application.tools;

import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchKnowledgeBaseTool implements NexusAiTool {

    private final KnowledgeService knowledgeService;

    public SearchKnowledgeBaseTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String getName() {
        return "searchKnowledgeBase";
    }

    @Override
    public String getDescription() {
        return "Search organization policy documents and standard operating procedures using semantic retrieval.";
    }

    @Override
    public String getRequiredPermission() {
        return ""; // Base permission accessible to all authenticated organization members
    }

    @Override
    public boolean isWriteAction() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        Object queryObj = arguments.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) {
            return ToolExecutionResult.failure("Missing search query");
        }

        try {
            List<SearchResultDto> results = knowledgeService.semanticSearch(
                    context.organizationId(),
                    queryObj.toString(),
                    5,
                    0.40
            );

            if (results.isEmpty()) {
                return ToolExecutionResult.success(List.of(), "No relevant organization policies found matching this query.");
            }

            String summary = results.stream()
                    .map(r -> String.format("[%s (Score: %.1f%%)]: %s",
                            r.citation(), r.similarityScore() * 100, r.content()))
                    .collect(Collectors.joining("\n\n"));

            return ToolExecutionResult.success(results, summary);
        } catch (Exception e) {
            return ToolExecutionResult.failure("Failed to search knowledge base: " + e.getMessage());
        }
    }
}

