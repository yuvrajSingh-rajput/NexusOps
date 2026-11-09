package com.nexusops.ai.application.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, NexusAiTool> toolsByName;

    public ToolRegistry(List<NexusAiTool> tools) {
        this.toolsByName = tools.stream()
                .collect(Collectors.toMap(NexusAiTool::getName, Function.identity()));
    }

    public Optional<NexusAiTool> getTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<NexusAiTool> getAvailableTools(ToolExecutionContext context) {
        return toolsByName.values().stream()
                .filter(t -> context.hasPermission(t.getRequiredPermission()))
                .toList();
    }
}

