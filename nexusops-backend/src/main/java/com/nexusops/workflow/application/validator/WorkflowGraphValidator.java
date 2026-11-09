package com.nexusops.workflow.application.validator;

import com.nexusops.shared.exception.ValidationException;
import com.nexusops.workflow.application.dto.WorkflowValidationResultDto;
import com.nexusops.workflow.domain.NodeType;
import com.nexusops.workflow.domain.WorkflowNodeEntity;
import com.nexusops.workflow.domain.WorkflowTransitionEntity;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkflowGraphValidator {

    public void validateGraph(List<WorkflowNodeEntity> nodes, List<WorkflowTransitionEntity> transitions) {
        WorkflowValidationResultDto result = validateGraphDetailed(nodes, transitions);
        if (!result.valid()) {
            throw new ValidationException("Workflow graph validation failed: " + String.join("; ", result.errors()));
        }
    }

    public WorkflowValidationResultDto validateGraphDetailed(List<WorkflowNodeEntity> nodes, List<WorkflowTransitionEntity> transitions) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {
            errors.add("Workflow graph must contain at least one node.");
            return new WorkflowValidationResultDto(false, errors, warnings);
        }

        // 1. Unique Node Keys
        Set<String> nodeKeys = new HashSet<>();
        Map<UUID, WorkflowNodeEntity> nodeMap = new HashMap<>();
        for (WorkflowNodeEntity node : nodes) {
            if (node.getId() != null) {
                nodeMap.put(node.getId(), node);
            }
            if (!nodeKeys.add(node.getNodeKey())) {
                errors.add("Duplicate node key: " + node.getNodeKey());
            }
        }

        // 2. Exactly one START node
        List<WorkflowNodeEntity> startNodes = nodes.stream()
                .filter(n -> n.getNodeType() == NodeType.START)
                .toList();
        if (startNodes.isEmpty()) {
            errors.add("Workflow graph must have exactly one START node (found 0).");
        } else if (startNodes.size() > 1) {
            errors.add("Workflow graph must have exactly one START node (found " + startNodes.size() + ").");
        }

        // 3. At least one END node
        List<WorkflowNodeEntity> endNodes = nodes.stream()
                .filter(n -> n.getNodeType() == NodeType.END)
                .toList();
        if (endNodes.isEmpty()) {
            errors.add("Workflow graph must have at least one END node.");
        }

        // 4. Transitions refer to valid nodes
        Map<UUID, List<WorkflowTransitionEntity>> outgoingEdges = new HashMap<>();
        Map<UUID, List<WorkflowTransitionEntity>> incomingEdges = new HashMap<>();

        if (transitions != null) {
            for (WorkflowTransitionEntity t : transitions) {
                if (!nodeMap.containsKey(t.getSourceNodeId())) {
                    errors.add("Transition references unknown source node: " + t.getSourceNodeId());
                }
                if (!nodeMap.containsKey(t.getTargetNodeId())) {
                    errors.add("Transition references unknown target node: " + t.getTargetNodeId());
                }

                outgoingEdges.computeIfAbsent(t.getSourceNodeId(), k -> new ArrayList<>()).add(t);
                incomingEdges.computeIfAbsent(t.getTargetNodeId(), k -> new ArrayList<>()).add(t);
            }
        }

        // 5. Check node connectivity and configurations
        for (WorkflowNodeEntity node : nodes) {
            if (node.getId() == null) continue;
            int outgoing = outgoingEdges.getOrDefault(node.getId(), Collections.emptyList()).size();
            int incoming = incomingEdges.getOrDefault(node.getId(), Collections.emptyList()).size();

            if (node.getNodeType() == NodeType.START) {
                if (incoming > 0) {
                    errors.add("START node '" + node.getNodeKey() + "' cannot have incoming transitions.");
                }
                if (outgoing == 0) {
                    errors.add("START node '" + node.getNodeKey() + "' must have at least one outgoing transition.");
                }
            } else if (node.getNodeType() == NodeType.END) {
                if (outgoing > 0) {
                    errors.add("END node '" + node.getNodeKey() + "' cannot have outgoing transitions.");
                }
                if (incoming == 0) {
                    errors.add("END node '" + node.getNodeKey() + "' has no incoming transitions.");
                }
            } else {
                if (incoming == 0) {
                    errors.add("Node '" + node.getNodeKey() + "' is unreachable (0 incoming transitions).");
                }
                if (outgoing == 0) {
                    errors.add("Node '" + node.getNodeKey() + "' is dead-ended (0 outgoing transitions).");
                }
            }

            // Node specific property validation
            Map<String, Object> config = node.getConfiguration();
            if (node.getNodeType() == NodeType.APPROVAL) {
                boolean hasApprover = config != null && (
                        config.containsKey("approverType") ||
                        config.containsKey("approverRole") ||
                        config.containsKey("assignedMembershipId")
                );
                if (!hasApprover) {
                    warnings.add("Approval node '" + node.getNodeKey() + "' does not specify an approver type or role.");
                }
            } else if (node.getNodeType() == NodeType.TASK) {
                boolean hasQueue = config != null && (
                        config.containsKey("taskQueue") ||
                        config.containsKey("queue")
                );
                if (!hasQueue) {
                    warnings.add("Task node '" + node.getNodeKey() + "' does not specify a fulfillment queue.");
                }
            } else if (node.getNodeType() == NodeType.CONDITION) {
                List<WorkflowTransitionEntity> edges = outgoingEdges.getOrDefault(node.getId(), Collections.emptyList());
                boolean hasDefault = edges.stream().anyMatch(WorkflowTransitionEntity::isDefault);
                if (!hasDefault && edges.size() > 1) {
                    warnings.add("Condition node '" + node.getNodeKey() + "' does not have an explicit default route.");
                }
            }
        }

        // 6. Reachability of at least one END node from START via BFS
        if (startNodes.size() == 1 && startNodes.get(0).getId() != null) {
            UUID startId = startNodes.get(0).getId();
            Set<UUID> visited = new HashSet<>();
            Queue<UUID> queue = new LinkedList<>();
            queue.add(startId);
            visited.add(startId);

            while (!queue.isEmpty()) {
                UUID curr = queue.poll();
                for (WorkflowTransitionEntity edge : outgoingEdges.getOrDefault(curr, Collections.emptyList())) {
                    if (visited.add(edge.getTargetNodeId())) {
                        queue.add(edge.getTargetNodeId());
                    }
                }
            }

            boolean anyEndReachable = endNodes.stream()
                    .anyMatch(e -> e.getId() != null && visited.contains(e.getId()));
            if (!anyEndReachable) {
                errors.add("No END node is reachable from the START node.");
            }
        }

        return new WorkflowValidationResultDto(errors.isEmpty(), errors, warnings);
    }
}

