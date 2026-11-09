package com.nexusops.workflow.application;

import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.workflow.application.dto.*;
import com.nexusops.workflow.application.validator.WorkflowGraphValidator;
import com.nexusops.workflow.domain.*;
import com.nexusops.workflow.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowVersionRepository versionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowGraphValidator graphValidator;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitionRepository,
                                     WorkflowVersionRepository versionRepository,
                                     WorkflowNodeRepository nodeRepository,
                                     WorkflowTransitionRepository transitionRepository,
                                     WorkflowGraphValidator graphValidator) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.graphValidator = graphValidator;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponseDto> getDefinitions(UUID organizationId) {
        return definitionRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::mapDefinitionToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponseDto getDefinition(UUID organizationId, UUID definitionId) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));
        return mapDefinitionToDto(def);
    }

    @Transactional(readOnly = true)
    public List<WorkflowVersionSummaryDto> getVersions(UUID organizationId, UUID definitionId) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        return versionRepository.findAllByWorkflowDefinitionIdOrderByVersionNumberDesc(def.getId()).stream()
                .map(v -> new WorkflowVersionSummaryDto(
                        v.getId(),
                        v.getVersionNumber(),
                        v.getStatus(),
                        v.getPublishedAt(),
                        v.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponseDto getVersion(UUID organizationId, UUID definitionId, int versionNumber) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());

        return mapVersionToDto(ver, nodes, transitions);
    }

    @Transactional
    public WorkflowDefinitionResponseDto createDefinition(UUID organizationId, CreateWorkflowDefinitionDto dto) {
        if (definitionRepository.existsByOrganizationIdAndCode(organizationId, dto.code())) {
            throw new ValidationException("Workflow with code '" + dto.code() + "' already exists in this organization.");
        }

        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                organizationId,
                dto.categoryId(),
                dto.code(),
                dto.name(),
                dto.description()
        );
        def = definitionRepository.save(def);

        // Auto-create initial draft version 1 with basic START and END nodes
        WorkflowVersionEntity ver = new WorkflowVersionEntity(def.getId(), 1);
        ver = versionRepository.save(ver);

        WorkflowNodeEntity start = new WorkflowNodeEntity(ver.getId(), "START", NodeType.START, "Request Submitted", Map.of());
        start.setPositionX(100);
        start.setPositionY(200);
        nodeRepository.save(start);

        WorkflowNodeEntity end = new WorkflowNodeEntity(ver.getId(), "END", NodeType.END, "Completed", Map.of("outcome", "COMPLETED"));
        end.setPositionX(500);
        end.setPositionY(200);
        nodeRepository.save(end);

        transitionRepository.save(new WorkflowTransitionEntity(ver.getId(), start.getId(), end.getId(), null, 0, true));

        return mapDefinitionToDto(def);
    }

    @Transactional
    public WorkflowVersionResponseDto createDraftVersion(UUID organizationId, UUID definitionId) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        List<WorkflowVersionEntity> allVersions = versionRepository.findAllByWorkflowDefinitionIdOrderByVersionNumberDesc(def.getId());
        int nextVersionNumber = allVersions.isEmpty() ? 1 : allVersions.get(0).getVersionNumber() + 1;

        WorkflowVersionEntity newDraft = new WorkflowVersionEntity(def.getId(), nextVersionNumber);
        newDraft.setStatus(WorkflowVersionStatus.DRAFT);
        newDraft = versionRepository.save(newDraft);

        if (!allVersions.isEmpty()) {
            WorkflowVersionEntity sourceVersion = allVersions.stream()
                    .filter(v -> v.getStatus() == WorkflowVersionStatus.PUBLISHED)
                    .findFirst()
                    .orElse(allVersions.get(0));

            List<WorkflowNodeEntity> sourceNodes = nodeRepository.findAllByWorkflowVersionId(sourceVersion.getId());
            List<WorkflowTransitionEntity> sourceTransitions = transitionRepository.findAllByWorkflowVersionId(sourceVersion.getId());

            Map<UUID, UUID> oldNodeIdToNewNodeId = new HashMap<>();
            List<WorkflowNodeEntity> clonedNodes = new ArrayList<>();

            for (WorkflowNodeEntity srcNode : sourceNodes) {
                WorkflowNodeEntity cloned = new WorkflowNodeEntity(
                        newDraft.getId(),
                        srcNode.getNodeKey(),
                        srcNode.getNodeType(),
                        srcNode.getTitle(),
                        new HashMap<>(srcNode.getConfiguration())
                );
                cloned.setPositionX(srcNode.getPositionX());
                cloned.setPositionY(srcNode.getPositionY());
                cloned = nodeRepository.save(cloned);
                oldNodeIdToNewNodeId.put(srcNode.getId(), cloned.getId());
                clonedNodes.add(cloned);
            }

            List<WorkflowTransitionEntity> clonedTransitions = new ArrayList<>();
            for (WorkflowTransitionEntity srcTrans : sourceTransitions) {
                UUID newSourceId = oldNodeIdToNewNodeId.get(srcTrans.getSourceNodeId());
                UUID newTargetId = oldNodeIdToNewNodeId.get(srcTrans.getTargetNodeId());
                if (newSourceId != null && newTargetId != null) {
                    WorkflowTransitionEntity cloned = new WorkflowTransitionEntity(
                            newDraft.getId(),
                            newSourceId,
                            newTargetId,
                            srcTrans.getConditionExpression() != null ? new HashMap<>(srcTrans.getConditionExpression()) : null,
                            srcTrans.getPriority(),
                            srcTrans.isDefault()
                    );
                    clonedTransitions.add(transitionRepository.save(cloned));
                }
            }

            log.info("Cloned {} nodes and {} transitions from v{} to draft v{} for workflow {}",
                    clonedNodes.size(), clonedTransitions.size(), sourceVersion.getVersionNumber(), nextVersionNumber, def.getCode());
            return mapVersionToDto(newDraft, clonedNodes, clonedTransitions);
        }

        return mapVersionToDto(newDraft, List.of(), List.of());
    }

    @Transactional
    public WorkflowVersionResponseDto saveDraft(UUID organizationId, UUID definitionId, int versionNumber, SaveWorkflowDraftDto dto) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        if (ver.getStatus() == WorkflowVersionStatus.PUBLISHED) {
            throw new ValidationException("Workflow version " + versionNumber + " is already published and immutable. Create a new draft version to make changes.");
        }

        // Delete existing transitions first, then nodes
        transitionRepository.deleteAllByWorkflowVersionId(ver.getId());
        nodeRepository.deleteAllByWorkflowVersionId(ver.getId());

        // Save nodes
        Map<String, WorkflowNodeEntity> keyToSavedNode = new HashMap<>();
        List<WorkflowNodeEntity> savedNodes = new ArrayList<>();

        for (SaveWorkflowNodeDto n : dto.nodes()) {
            WorkflowNodeEntity node = new WorkflowNodeEntity(
                    ver.getId(),
                    n.nodeKey().trim(),
                    n.nodeType(),
                    n.name().trim(),
                    n.configuration() != null ? n.configuration() : new HashMap<>()
            );
            node.setPositionX(n.positionX());
            node.setPositionY(n.positionY());
            node = nodeRepository.save(node);
            keyToSavedNode.put(node.getNodeKey(), node);
            savedNodes.add(node);
        }

        // Save transitions
        List<WorkflowTransitionEntity> savedTransitions = new ArrayList<>();
        if (dto.transitions() != null) {
            for (SaveWorkflowTransitionDto t : dto.transitions()) {
                WorkflowNodeEntity src = keyToSavedNode.get(t.sourceNodeKey().trim());
                WorkflowNodeEntity tgt = keyToSavedNode.get(t.targetNodeKey().trim());

                if (src == null || tgt == null) {
                    log.warn("Skipping transition with missing node reference: source={}, target={}",
                            t.sourceNodeKey(), t.targetNodeKey());
                    continue;
                }

                WorkflowTransitionEntity trans = new WorkflowTransitionEntity(
                        ver.getId(),
                        src.getId(),
                        tgt.getId(),
                        t.conditionExpression(),
                        t.priority(),
                        t.isDefault()
                );
                savedTransitions.add(transitionRepository.save(trans));
            }
        }

        log.info("Saved workflow draft v{} for definition {} ({} nodes, {} transitions)",
                versionNumber, def.getCode(), savedNodes.size(), savedTransitions.size());

        return mapVersionToDto(ver, savedNodes, savedTransitions);
    }

    @Transactional(readOnly = true)
    public WorkflowValidationResultDto validateVersion(UUID organizationId, UUID definitionId, int versionNumber) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());

        return graphValidator.validateGraphDetailed(nodes, transitions);
    }

    @Transactional
    public WorkflowVersionResponseDto publishVersion(UUID organizationId, UUID definitionId, int versionNumber) {
        WorkflowDefinitionEntity def = definitionRepository.findByIdAndOrganizationId(definitionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow definition not found: " + definitionId));

        WorkflowVersionEntity ver = versionRepository.findByWorkflowDefinitionIdAndVersionNumber(def.getId(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow version " + versionNumber + " not found"));

        if (ver.getStatus() == WorkflowVersionStatus.PUBLISHED) {
            throw new ValidationException("Workflow version " + versionNumber + " is already published and immutable.");
        }

        List<WorkflowNodeEntity> nodes = nodeRepository.findAllByWorkflowVersionId(ver.getId());
        List<WorkflowTransitionEntity> transitions = transitionRepository.findAllByWorkflowVersionId(ver.getId());

        // Strictly validate graph rules
        graphValidator.validateGraph(nodes, transitions);

        // Retire previously published versions for this definition
        versionRepository.findByWorkflowDefinitionIdAndStatus(def.getId(), WorkflowVersionStatus.PUBLISHED)
                .ifPresent(prev -> {
                    prev.setStatus(WorkflowVersionStatus.RETIRED);
                    versionRepository.save(prev);
                });

        ver.setStatus(WorkflowVersionStatus.PUBLISHED);
        ver.setPublishedAt(Instant.now());

        // Snapshot graph compilation
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("nodes", nodes.stream().map(n -> Map.of(
                "key", n.getNodeKey(),
                "type", n.getNodeType().name(),
                "title", n.getTitle(),
                "configuration", n.getConfiguration()
        )).toList());
        ver.setDefinitionSnapshot(snapshot);

        ver = versionRepository.save(ver);
        log.info("Published immutable workflow version {} for definition {}", versionNumber, def.getCode());

        return mapVersionToDto(ver, nodes, transitions);
    }

    @Transactional
    public void seedReferenceLaptopWorkflow(UUID organizationId, UUID categoryId) {
        if (definitionRepository.findByOrganizationIdAndCode(organizationId, "WF_DEVELOPER_LAPTOP").isPresent()) {
            return;
        }

        log.info("Bootstrapping reference Laptop Purchase workflow for organization {}", organizationId);

        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                organizationId,
                categoryId,
                "WF_DEVELOPER_LAPTOP",
                "Developer Laptop Purchase Workflow",
                "Automated multi-tier approval routing for laptop hardware requisitions ($10k / $50k thresholds)"
        );
        def = definitionRepository.save(def);

        WorkflowVersionEntity ver = new WorkflowVersionEntity(def.getId(), 1);
        ver = versionRepository.save(ver);

        // 1. Nodes with React Flow Canvas coordinates
        WorkflowNodeEntity nStart = new WorkflowNodeEntity(
                ver.getId(), "START", NodeType.START, "Request Submitted", Map.of()
        );
        nStart.setPositionX(80);
        nStart.setPositionY(220);
        nStart = nodeRepository.save(nStart);

        WorkflowNodeEntity nManager = new WorkflowNodeEntity(
                ver.getId(), "MGR_APPROVAL", NodeType.APPROVAL, "Direct Manager Approval",
                Map.of("approverType", "MANAGER", "timeoutHours", 48)
        );
        nManager.setPositionX(320);
        nManager.setPositionY(220);
        nManager = nodeRepository.save(nManager);

        WorkflowNodeEntity nCond = new WorkflowNodeEntity(
                ver.getId(), "COND_AMOUNT", NodeType.CONDITION, "Evaluate Purchase Amount",
                Map.of("field", "payload.estimatedAmount")
        );
        nCond.setPositionX(580);
        nCond.setPositionY(220);
        nCond = nodeRepository.save(nCond);

        WorkflowNodeEntity nTask = new WorkflowNodeEntity(
                ver.getId(), "TASK_PROCUREMENT", NodeType.TASK, "IT Procurement & Provisioning",
                Map.of("taskQueue", "IT_OPERATIONS", "slaHours", 120, "priority", "MEDIUM")
        );
        nTask.setPositionX(860);
        nTask.setPositionY(80);
        nTask = nodeRepository.save(nTask);

        WorkflowNodeEntity nFinance = new WorkflowNodeEntity(
                ver.getId(), "FIN_APPROVAL", NodeType.APPROVAL, "Finance Budget Approval",
                Map.of("approverRole", "FINANCE", "timeoutHours", 72)
        );
        nFinance.setPositionX(860);
        nFinance.setPositionY(220);
        nFinance = nodeRepository.save(nFinance);

        WorkflowNodeEntity nDeptHead = new WorkflowNodeEntity(
                ver.getId(), "HEAD_APPROVAL", NodeType.APPROVAL, "Department Head Approval",
                Map.of("approverType", "DEPT_HEAD", "timeoutHours", 72)
        );
        nDeptHead.setPositionX(860);
        nDeptHead.setPositionY(360);
        nDeptHead = nodeRepository.save(nDeptHead);

        WorkflowNodeEntity nEndCompleted = new WorkflowNodeEntity(
                ver.getId(), "END_COMPLETED", NodeType.END, "Request Completed",
                Map.of("outcome", "COMPLETED")
        );
        nEndCompleted.setPositionX(1160);
        nEndCompleted.setPositionY(140);
        nEndCompleted = nodeRepository.save(nEndCompleted);

        WorkflowNodeEntity nEndRejected = new WorkflowNodeEntity(
                ver.getId(), "END_REJECTED", NodeType.END, "Request Rejected",
                Map.of("outcome", "REJECTED")
        );
        nEndRejected.setPositionX(1160);
        nEndRejected.setPositionY(380);
        nEndRejected = nodeRepository.save(nEndRejected);

        // 2. Transitions with clear conditions
        // START -> MGR_APPROVAL
        transitionRepository.save(new WorkflowTransitionEntity(ver.getId(), nStart.getId(), nManager.getId(), null, 0, true));

        // MGR_APPROVAL -> COND_AMOUNT (on approve)
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nManager.getId(), nCond.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        // MGR_APPROVAL -> END_REJECTED (on reject)
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nManager.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // COND_AMOUNT Branches:
        // Branch 1: <= 10000 -> IT Task directly
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nTask.getId(),
                Map.of("field", "payload.estimatedAmount", "operator", "LESS_THAN_OR_EQUAL", "value", 10000),
                1, false
        ));
        // Branch 2: Compound AND: > 10000 AND <= 50000 -> Finance Approval
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nFinance.getId(),
                Map.of(
                        "logicalOperator", "AND",
                        "conditions", List.of(
                                Map.of("field", "payload.estimatedAmount", "operator", "GREATER_THAN", "value", 10000),
                                Map.of("field", "payload.estimatedAmount", "operator", "LESS_THAN_OR_EQUAL", "value", 50000)
                        )
                ),
                2, false
        ));
        // Branch 3: Fallback default route (> 50000) -> Dept Head Approval
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nCond.getId(), nDeptHead.getId(),
                Map.of("field", "payload.estimatedAmount", "operator", "GREATER_THAN", "value", 50000),
                3, true
        ));

        // FIN_APPROVAL Transitions:
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nFinance.getId(), nTask.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nFinance.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // HEAD_APPROVAL Transitions:
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nDeptHead.getId(), nTask.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "APPROVED"),
                1, true
        ));
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nDeptHead.getId(), nEndRejected.getId(),
                Map.of("field", "context.signal", "operator", "EQUALS", "value", "REJECTED"),
                2, false
        ));

        // TASK -> END_COMPLETED
        transitionRepository.save(new WorkflowTransitionEntity(
                ver.getId(), nTask.getId(), nEndCompleted.getId(), null, 0, true
        ));

        // Publish v1
        publishVersion(organizationId, def.getId(), 1);
    }

    private WorkflowDefinitionResponseDto mapDefinitionToDto(WorkflowDefinitionEntity def) {
        int latestNum = 1;
        WorkflowVersionStatus latestStatus = WorkflowVersionStatus.DRAFT;

        Optional<WorkflowVersionEntity> latestVer = versionRepository
                .findAllByWorkflowDefinitionIdOrderByVersionNumberDesc(def.getId())
                .stream().findFirst();

        if (latestVer.isPresent()) {
            latestNum = latestVer.get().getVersionNumber();
            latestStatus = latestVer.get().getStatus();
        }

        return new WorkflowDefinitionResponseDto(
                def.getId(),
                def.getOrganizationId(),
                def.getCategoryId(),
                def.getCode(),
                def.getName(),
                def.getDescription(),
                def.isActive(),
                latestNum,
                latestStatus,
                def.getCreatedAt()
        );
    }

    private WorkflowVersionResponseDto mapVersionToDto(WorkflowVersionEntity ver,
                                                       List<WorkflowNodeEntity> nodes,
                                                       List<WorkflowTransitionEntity> transitions) {
        List<WorkflowNodeDto> nodeDtos = nodes.stream()
                .map(n -> new WorkflowNodeDto(
                        n.getId(), n.getNodeKey(), n.getNodeType(), n.getTitle(),
                        n.getConfiguration(), n.getPositionX(), n.getPositionY()
                )).toList();

        List<WorkflowTransitionDto> transitionDtos = transitions.stream()
                .map(t -> new WorkflowTransitionDto(
                        t.getId(), t.getSourceNodeId(), t.getTargetNodeId(),
                        t.getConditionExpression(), t.getPriority(), t.isDefault()
                )).toList();

        return new WorkflowVersionResponseDto(
                ver.getId(),
                ver.getWorkflowDefinitionId(),
                ver.getVersionNumber(),
                ver.getStatus(),
                ver.getPublishedAt(),
                nodeDtos,
                transitionDtos,
                ver.getCreatedAt()
        );
    }
}
