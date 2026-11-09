package com.nexusops.workflow.application;

import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.workflow.application.dto.NodeInstanceResponseDto;
import com.nexusops.workflow.application.dto.WorkflowInstanceResponseDto;
import com.nexusops.workflow.application.evaluator.TypedConditionEvaluator;
import com.nexusops.workflow.application.handler.NodeExecutionResult;
import com.nexusops.workflow.application.handler.NodeHandler;
import com.nexusops.workflow.application.handler.NodeHandlerRegistry;
import com.nexusops.workflow.application.handler.WorkflowExecutionContext;
import com.nexusops.workflow.domain.*;
import com.nexusops.workflow.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowEngineService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineService.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowDefinitionService definitionService;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowNodeInstanceRepository nodeInstanceRepository;
    private final NodeHandlerRegistry handlerRegistry;
    private final TypedConditionEvaluator conditionEvaluator;

    public WorkflowEngineService(WorkflowDefinitionRepository definitionRepository,
                                 WorkflowDefinitionService definitionService,
                                 WorkflowVersionRepository versionRepository,
                                 WorkflowNodeRepository nodeRepository,
                                 WorkflowTransitionRepository transitionRepository,
                                 WorkflowInstanceRepository instanceRepository,
                                 WorkflowNodeInstanceRepository nodeInstanceRepository,
                                 NodeHandlerRegistry handlerRegistry,
                                 TypedConditionEvaluator conditionEvaluator) {
        this.definitionRepository = definitionRepository;
        this.definitionService = definitionService;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.instanceRepository = instanceRepository;
        this.nodeInstanceRepository = nodeInstanceRepository;
        this.handlerRegistry = handlerRegistry;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Transactional
    public WorkflowInstanceResponseDto startWorkflow(UUID organizationId, UUID requestId, UUID categoryId, Map<String, Object> payload) {
        log.info("Starting workflow for request {} in organization {}", requestId, organizationId);

        WorkflowDefinitionEntity def = definitionRepository.findByOrganizationIdAndCategoryIdAndActiveTrue(organizationId, categoryId)
                .orElseGet(() -> {
                    definitionService.seedReferenceLaptopWorkflow(organizationId, categoryId);
                    return definitionRepository.findByOrganizationIdAndCategoryIdAndActiveTrue(organizationId, categoryId)
                            .orElseThrow(() -> new ValidationException("No active workflow definition configured for category: " + categoryId));
                });

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndStatus(def.getId(), WorkflowVersionStatus.PUBLISHED)
                .orElseThrow(() -> new ValidationException("Workflow definition '" + def.getName() + "' does not have a published version."));

        // Check if active workflow already exists for this request
        if (instanceRepository.findByOrganizationIdAndRequestId(organizationId, requestId).isPresent()) {
            throw new ValidationException("A workflow instance already exists for request: " + requestId);
        }

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity(organizationId, ver.getId(), requestId);
        Map<String, Object> initialContext = new HashMap<>();
        initialContext.put("payload", payload != null ? payload : new HashMap<>());
        initialContext.put("requestId", requestId.toString());
        initialContext.put("organizationId", organizationId.toString());
        instance.setContextData(initialContext);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance = instanceRepository.save(instance);

        WorkflowNodeEntity startNode = nodeRepository.findByWorkflowVersionIdAndNodeKey(ver.getId(), "START")
                .orElseThrow(() -> new IllegalStateException("Corrupt workflow graph: START node missing for version: " + ver.getId()));

        advanceWorkflow(instance, startNode, initialContext);

        instance = instanceRepository.save(instance);
        return mapInstanceToDto(instance);
    }

    @Transactional
    public WorkflowInstanceResponseDto resumeWorkflowWithSignal(UUID organizationId, UUID workflowInstanceId,
                                                                String signal, Map<String, Object> signalData) {
        log.info("Delivering signal '{}' to workflow instance {} in organization {}", signal, workflowInstanceId, organizationId);

        WorkflowInstanceEntity instance = instanceRepository.findByIdAndOrganizationId(workflowInstanceId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + workflowInstanceId));

        if (instance.getStatus() != WorkflowStatus.WAITING && instance.getStatus() != WorkflowStatus.RUNNING) {
            throw new ValidationException("Cannot signal workflow in non-active state: " + instance.getStatus());
        }

        String nodeKey = instance.getCurrentNodeKey();
        UUID versionId = instance.getWorkflowVersionId();
        WorkflowNodeEntity currentNode = nodeRepository.findByWorkflowVersionIdAndNodeKey(versionId, nodeKey)
                .orElseThrow(() -> new IllegalStateException("Current node not found: " + nodeKey));

        WorkflowNodeInstanceEntity activeNodeInst = nodeInstanceRepository
                .findFirstByWorkflowInstanceIdAndNodeIdOrderByEnteredAtDesc(instance.getId(), currentNode.getId())
                .orElseThrow(() -> new IllegalStateException("Active node instance not found for node: " + currentNode.getNodeKey()));

        // Merge signal and signal data into context
        Map<String, Object> contextData = instance.getContextData();
        Map<String, Object> contextSignalMap = new HashMap<>();
        contextSignalMap.put("signal", signal);
        if (signalData != null) {
            contextSignalMap.putAll(signalData);
        }
        contextData.put("context", contextSignalMap);
        instance.setContextData(contextData);

        WorkflowExecutionContext executionContext = new WorkflowExecutionContext(
                organizationId, instance.getRequestId(), instance.getId(),
                activeNodeInst.getId(), currentNode, contextData
        );
        executionContext.setSignal(signal);
        executionContext.setSignalData(signalData);

        NodeHandler handler = handlerRegistry.getHandler(currentNode.getNodeType());
        NodeExecutionResult result = handler.handleSignal(executionContext);

        if (result.getStatus() == NodeExecutionResult.Status.WAITING) {
            activeNodeInst.setStatus(NodeInstanceStatus.WAITING);
            activeNodeInst.setResult(result.getResultData());
            nodeInstanceRepository.save(activeNodeInst);
            instance.setStatus(WorkflowStatus.WAITING);
            instance = instanceRepository.save(instance);
            return mapInstanceToDto(instance);
        }

        if (result.getStatus() == NodeExecutionResult.Status.TERMINATED) {
            activeNodeInst.setStatus(NodeInstanceStatus.COMPLETED);
            activeNodeInst.setExitedAt(Instant.now());
            activeNodeInst.setResult(Map.of("signal", signal, "termination", result.getTerminationReason()));
            nodeInstanceRepository.save(activeNodeInst);

            instance.setStatus(WorkflowStatus.REJECTED);
            instance.setCompletedAt(Instant.now());
            instance = instanceRepository.save(instance);
            log.info("Workflow instance {} rejected after signal: {}", instance.getId(), signal);
            return mapInstanceToDto(instance);
        }

        // Node completed via signal -> mark active node completed and advance to next node
        activeNodeInst.setStatus(NodeInstanceStatus.COMPLETED);
        activeNodeInst.setExitedAt(Instant.now());
        activeNodeInst.setResult(Map.of("signal", signal));
        nodeInstanceRepository.save(activeNodeInst);

        WorkflowNodeEntity nextNode = resolveNextNode(currentNode, contextData);
        advanceWorkflow(instance, nextNode, contextData);

        instance = instanceRepository.save(instance);
        return mapInstanceToDto(instance);
    }

    private void advanceWorkflow(WorkflowInstanceEntity instance, WorkflowNodeEntity currentNode, Map<String, Object> contextData) {
        while (currentNode != null) {
            instance.setCurrentNodeKey(currentNode.getNodeKey());

            WorkflowNodeInstanceEntity nodeInst = new WorkflowNodeInstanceEntity(
                    instance.getId(), currentNode.getId(), NodeInstanceStatus.RUNNING
            );
            nodeInst = nodeInstanceRepository.save(nodeInst);

            WorkflowExecutionContext executionContext = new WorkflowExecutionContext(
                    instance.getOrganizationId(), instance.getRequestId(), instance.getId(),
                    nodeInst.getId(), currentNode, contextData
            );

            NodeHandler handler = handlerRegistry.getHandler(currentNode.getNodeType());
            NodeExecutionResult result = handler.execute(executionContext);

            if (result.getStatus() == NodeExecutionResult.Status.WAITING) {
                nodeInst.setStatus(NodeInstanceStatus.WAITING);
                nodeInst.setResult(result.getResultData());
                nodeInstanceRepository.save(nodeInst);

                instance.setStatus(WorkflowStatus.WAITING);
                log.info("Workflow instance {} paused at node {} in WAITING state", instance.getId(), currentNode.getNodeKey());
                return; // Durable stop: execution thread exits, waiting for human action
            }

            if (result.getStatus() == NodeExecutionResult.Status.TERMINATED) {
                nodeInst.setStatus(NodeInstanceStatus.COMPLETED);
                nodeInst.setExitedAt(Instant.now());
                nodeInst.setResult(Map.of("outcome", "TERMINATED", "reason", result.getTerminationReason()));
                nodeInstanceRepository.save(nodeInst);

                instance.setStatus(WorkflowStatus.REJECTED);
                instance.setCompletedAt(Instant.now());
                log.info("Workflow instance {} terminated as REJECTED at node {}", instance.getId(), currentNode.getNodeKey());
                return;
            }

            // Node completed immediately
            nodeInst.setStatus(NodeInstanceStatus.COMPLETED);
            nodeInst.setExitedAt(Instant.now());
            nodeInst.setResult(result.getResultData());
            nodeInstanceRepository.save(nodeInst);

            if (currentNode.getNodeType() == NodeType.END) {
                instance.setStatus(WorkflowStatus.COMPLETED);
                instance.setCompletedAt(Instant.now());
                log.info("Workflow instance {} successfully reached END node and completed", instance.getId());
                return;
            }

            // Advance through outbound transition
            currentNode = resolveNextNode(currentNode, contextData);
        }
    }

    private WorkflowNodeEntity resolveNextNode(WorkflowNodeEntity currentNode, Map<String, Object> contextData) {
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllBySourceNodeIdOrderByPriorityAsc(currentNode.getId());
        if (transitions.isEmpty()) {
            throw new IllegalStateException("Dead end reached in workflow graph at node: " + currentNode.getNodeKey());
        }

        WorkflowTransitionEntity selectedTransition = null;

        for (WorkflowTransitionEntity t : transitions) {
            if (t.getConditionExpression() != null && !t.getConditionExpression().isEmpty()) {
                boolean matches = conditionEvaluator.evaluate(t.getConditionExpression(), contextData);
                if (matches) {
                    selectedTransition = t;
                    break;
                }
            }
        }

        if (selectedTransition == null) {
            // Check default transition
            selectedTransition = transitions.stream()
                    .filter(WorkflowTransitionEntity::isDefault)
                    .findFirst()
                    .orElse(null);
        }

        if (selectedTransition == null) {
            throw new IllegalStateException("No valid transition condition matched from node: " + currentNode.getNodeKey());
        }

        UUID targetNodeId = selectedTransition.getTargetNodeId();
        return nodeRepository.findById(targetNodeId)
                .orElseThrow(() -> new IllegalStateException("Target node not found: " + targetNodeId));
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowInstanceResponseDto> getWorkflowInstanceForRequest(UUID organizationId, UUID requestId) {
        return instanceRepository.findByOrganizationIdAndRequestId(organizationId, requestId)
                .map(this::mapInstanceToDto);
    }

    @Transactional(readOnly = true)
    public List<NodeInstanceResponseDto> getNodeInstances(UUID organizationId, UUID workflowInstanceId) {
        WorkflowInstanceEntity instance = instanceRepository.findByIdAndOrganizationId(workflowInstanceId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + workflowInstanceId));

        return nodeInstanceRepository.findAllByWorkflowInstanceIdOrderByEnteredAtAsc(instance.getId()).stream()
                .map(ni -> {
                    WorkflowNodeEntity node = nodeRepository.findById(ni.getNodeId()).orElse(null);
                    return new NodeInstanceResponseDto(
                            ni.getId(),
                            ni.getWorkflowInstanceId(),
                            ni.getNodeId(),
                            node != null ? node.getNodeKey() : "UNKNOWN",
                            node != null ? node.getTitle() : "Unknown Node",
                            node != null ? node.getNodeType() : NodeType.TASK,
                            ni.getStatus(),
                            ni.getResult(),
                            ni.getEnteredAt(),
                            ni.getExitedAt()
                    );
                }).toList();
    }

    private WorkflowInstanceResponseDto mapInstanceToDto(WorkflowInstanceEntity inst) {
        return new WorkflowInstanceResponseDto(
                inst.getId(),
                inst.getOrganizationId(),
                inst.getWorkflowVersionId(),
                inst.getRequestId(),
                inst.getAttemptNumber(),
                inst.getStatus(),
                inst.getCurrentNodeKey(),
                inst.getContextData(),
                inst.getStartedAt(),
                inst.getCompletedAt()
        );
    }
}
