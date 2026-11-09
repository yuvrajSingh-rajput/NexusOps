package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeHandlerRegistry {

    private final Map<NodeType, NodeHandler> handlers;

    public NodeHandlerRegistry(List<NodeHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(NodeHandler::getNodeType, Function.identity()));
    }

    public NodeHandler getHandler(NodeType type) {
        NodeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for node type: " + type);
        }
        return handler;
    }
}
