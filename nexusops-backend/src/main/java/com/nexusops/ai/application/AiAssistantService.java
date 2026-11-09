package com.nexusops.ai.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.ai.application.dto.ActionProposalDto;
import com.nexusops.ai.application.dto.ChatMessageDto;
import com.nexusops.ai.application.dto.ChatRequestDto;
import com.nexusops.ai.application.dto.ChatResponseDto;
import com.nexusops.ai.application.dto.ConfirmActionDto;
import com.nexusops.ai.application.dto.ConversationSummaryDto;
import com.nexusops.ai.application.tools.NexusAiTool;
import com.nexusops.ai.application.tools.ToolExecutionContext;
import com.nexusops.ai.application.tools.ToolExecutionResult;
import com.nexusops.ai.application.tools.ToolRegistry;
import com.nexusops.ai.domain.AiConversationEntity;
import com.nexusops.ai.domain.AiMessageEntity;
import com.nexusops.ai.domain.AiMessageRole;
import com.nexusops.ai.domain.AiToolExecutionEntity;
import com.nexusops.ai.domain.ToolExecutionStatus;
import com.nexusops.ai.infrastructure.AiConversationRepository;
import com.nexusops.ai.infrastructure.AiMessageRepository;
import com.nexusops.ai.infrastructure.AiToolExecutionRepository;
import com.nexusops.knowledge.application.KnowledgeService;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiToolExecutionRepository toolExecutionRepository;
    private final ToolRegistry toolRegistry;
    private final IntentClassifier intentClassifier;
    private final RagContextBuilder ragContextBuilder;
    private final AiModelProvider aiModelProvider;
    private final KnowledgeService knowledgeService;
    private final RequestService requestService;
    private final RequestCategoryService requestCategoryService;
    private final ObjectMapper objectMapper;

    public AiAssistantService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AiToolExecutionRepository toolExecutionRepository,
            ToolRegistry toolRegistry,
            IntentClassifier intentClassifier,
            RagContextBuilder ragContextBuilder,
            AiModelProvider aiModelProvider,
            KnowledgeService knowledgeService,
            RequestService requestService,
            RequestCategoryService requestCategoryService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.toolExecutionRepository = toolExecutionRepository;
        this.toolRegistry = toolRegistry;
        this.intentClassifier = intentClassifier;
        this.ragContextBuilder = ragContextBuilder;
        this.aiModelProvider = aiModelProvider;
        this.knowledgeService = knowledgeService;
        this.requestService = requestService;
        this.requestCategoryService = requestCategoryService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process an incoming user chat message with intent routing, RAG grounding, or tool execution.
     */
    public ChatResponseDto chat(UserPrincipal principal, ChatRequestDto dto) {
        UUID organizationId = principal.getOrganizationId();
        UUID userId = principal.getUserId();
        UUID membershipId = principal.getMembershipId();

        // 1. Resolve or Create Conversation
        AiConversationEntity conversation;
        if (dto.conversationId() != null) {
            conversation = conversationRepository.findByOrganizationIdAndId(organizationId, dto.conversationId())
                    .orElseGet(() -> createConversation(organizationId, userId, dto.message()));
        } else {
            conversation = createConversation(organizationId, userId, dto.message());
        }

        // 2. Persist User Message
        AiMessageEntity userMsg = new AiMessageEntity(
                organizationId, conversation, AiMessageRole.USER, dto.message(), null, null);
        messageRepository.save(userMsg);
        conversation.addMessage(userMsg);

        // 3. Classify Intent
        IntentClassifier.Intent intent = intentClassifier.classify(dto.message());
        ToolExecutionContext context = new ToolExecutionContext(organizationId, userId, membershipId, principal);

        String reply;
        List<SearchResultDto> citations = List.of();
        ActionProposalDto proposal = null;
        String toolUsed = null;

        switch (intent) {
            case SEARCH_REQUESTS -> {
                toolUsed = "searchMyRequests";
                Optional<NexusAiTool> toolOpt = toolRegistry.getTool(toolUsed);
                if (toolOpt.isPresent()) {
                    ToolExecutionResult res = toolOpt.get().execute(Map.of(), context);
                    reply = res.summary();
                } else {
                    reply = "Unable to access requests tool.";
                }
            }
            case SEARCH_APPROVALS -> {
                toolUsed = "searchPendingApprovals";
                Optional<NexusAiTool> toolOpt = toolRegistry.getTool(toolUsed);
                if (toolOpt.isPresent() && context.hasPermission("APPROVAL_EXECUTE")) {
                    ToolExecutionResult res = toolOpt.get().execute(Map.of(), context);
                    reply = res.summary();
                } else {
                    reply = "You do not have permission to view approval queues.";
                }
            }
            case SEARCH_TASKS -> {
                toolUsed = "searchMyTasks";
                Optional<NexusAiTool> toolOpt = toolRegistry.getTool(toolUsed);
                if (toolOpt.isPresent() && context.hasPermission("TASK_OPERATE")) {
                    ToolExecutionResult res = toolOpt.get().execute(Map.of(), context);
                    reply = res.summary();
                } else {
                    reply = "You do not have operator permissions to view fulfillment tasks.";
                }
            }
            case CREATE_REQUEST -> {
                if (!context.hasPermission("REQUEST_CREATE")) {
                    reply = "You do not have permission to create requests in this organization.";
                } else {
                    toolUsed = "createRequest";
                    proposal = prepareCreateRequestProposal(organizationId, conversation.getId(), userId, dto.message());
                    reply = String.format("I have prepared an action proposal to create your request: \"%s\".\n" +
                            "Please review the proposed parameters in the action card below and click Confirm to submit.",
                            proposal.title());
                }
            }
            case DEPARTMENT_REPORT -> {
                toolUsed = "generateDepartmentReport";
                Optional<NexusAiTool> toolOpt = toolRegistry.getTool(toolUsed);
                if (toolOpt.isPresent()) {
                    String deptCode = extractDepartmentCode(dto.message());
                    ToolExecutionResult res = toolOpt.get().execute(Map.of("departmentCode", deptCode), context);
                    reply = res.summary();
                } else {
                    reply = "Unable to access department analytics report tool.";
                }
            }
            case POLICY_QA -> {
                // Grounded RAG Semantic Search
                citations = knowledgeService.semanticSearch(organizationId, dto.message(), 5, 0.40);
                if (!citations.isEmpty()) {
                    String systemPrompt = ragContextBuilder.buildPromptContext(citations, dto.message());
                    reply = aiModelProvider.generateAnswer(systemPrompt, dto.message(), citations);
                } else {
                    reply = "I cannot find an answer in your company's policy documents. Please contact HR or IT for assistance.";
                }
            }
            case CONVERSATION -> {
                reply = "Hello! I am **Nexus AI**, your enterprise operations co-pilot.\n\n" +
                        "Here is what I can assist you with:\n" +
                        "- **Policy Inquiries (RAG):** Ask questions about hardware budgets, leaves, or reimbursement rules.\n" +
                        "- **Track Requests:** Ask *\"What is the status of my requests?\"*\n" +
                        "- **Approvals & Tasks:** Ask *\"Show my pending approvals\"* or *\"Show my tasks\"*.\n" +
                        "- **Analytics & Reports:** Ask *\"Generate department report for Engineering\"* or *\"Show operational metrics\"*.\n" +
                        "- **Create Requests:** Ask *\"I need a new developer laptop\"* to prepare a submission card.";
            }
            default -> {
                reply = "How can I help you navigate NexusOps today?";
            }
        }

        // 4. Persist Assistant Response
        String citationsJson = serializeCitations(citations);
        String toolCallJson = (proposal != null) ? serializeProposal(proposal) : null;

        AiMessageEntity assistantMsg = new AiMessageEntity(
                organizationId, conversation, AiMessageRole.ASSISTANT, reply, citationsJson, toolCallJson);
        messageRepository.save(assistantMsg);
        conversation.addMessage(assistantMsg);

        return new ChatResponseDto(
                conversation.getId(),
                assistantMsg.getId(),
                reply,
                citations,
                proposal,
                toolUsed
        );
    }

    private ActionProposalDto prepareCreateRequestProposal(UUID organizationId, UUID conversationId,
                                                           UUID userId, String prompt) {
        String cleanTitle = prompt
                .replaceAll("(?i)^(i need|request a|order a|please create|create request for)\\s*", "")
                .trim();
        if (cleanTitle.isBlank()) {
            cleanTitle = "Service Request";
        }
        cleanTitle = Character.toUpperCase(cleanTitle.charAt(0)) + cleanTitle.substring(1);

        Map<String, Object> params = new HashMap<>();
        params.put("title", cleanTitle);
        params.put("priority", "MEDIUM");
        params.put("estimatedAmount", 1200.00);

        String argsJson;
        try {
            argsJson = objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            argsJson = "{}";
        }

        AiToolExecutionEntity execution = new AiToolExecutionEntity(
                organizationId, conversationId, userId, "createRequest", argsJson, true, 15);
        execution = toolExecutionRepository.save(execution);

        return new ActionProposalDto(
                execution.getId(),
                execution.getToolName(),
                cleanTitle,
                "Automatic request creation proposal generated by Nexus AI co-pilot.",
                params,
                execution.getExpiresAt(),
                execution.getStatus()
        );
    }

    /**
     * Two-phase action confirmation handler.
     */
    public RequestResponseDto confirmAction(UserPrincipal principal, UUID actionId, ConfirmActionDto dto) {
        UUID organizationId = principal.getOrganizationId();
        UUID userId = principal.getUserId();

        AiToolExecutionEntity action = toolExecutionRepository.findByOrganizationIdAndId(organizationId, actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Action proposal not found with ID: " + actionId));

        if (!action.getUserId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to confirm this action proposal.");
        }

        if (action.getStatus() != ToolExecutionStatus.PROPOSED && action.getStatus() != ToolExecutionStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("Action proposal is not in confirmable state (current: " + action.getStatus() + ")");
        }

        if (action.isExpired()) {
            action.setStatus(ToolExecutionStatus.EXPIRED);
            toolExecutionRepository.save(action);
            throw new IllegalStateException("Action proposal has expired. Please ask Nexus AI to propose it again.");
        }

        if (!dto.confirm()) {
            action.setStatus(ToolExecutionStatus.REJECTED);
            action.setErrorCode("USER_REJECTED");
            toolExecutionRepository.save(action);
            log.info("Action proposal {} rejected by user {}", actionId, userId);
            return null;
        }

        action.setStatus(ToolExecutionStatus.EXECUTING);
        toolExecutionRepository.save(action);

        try {
            TenantContextHolder.setTenantId(organizationId);

            if ("createRequest".equals(action.getToolName())) {
                JsonNode argsNode = objectMapper.readTree(action.getArguments());
                if (argsNode.isTextual()) {
                    argsNode = objectMapper.readTree(argsNode.asText());
                }
                Map<String, Object> args = objectMapper.convertValue(argsNode, new TypeReference<Map<String, Object>>() {});
                String title = (String) args.getOrDefault("title", "Service Request");
                String priorityStr = (String) args.getOrDefault("priority", "MEDIUM");

                // Resolve active categories
                List<RequestCategoryResponseDto> categories = requestCategoryService.getActiveCategories();
                UUID categoryId;
                if (!categories.isEmpty()) {
                    categoryId = categories.get(0).id();
                } else {
                    String schema = """
                            {
                              "$schema": "http://json-schema.org/draft-07/schema#",
                              "type": "object",
                              "properties": {
                                "urgencyReason": { "type": "string" },
                                "ramGb": { "type": "integer" },
                                "storage": { "type": "string" }
                              },
                              "required": ["urgencyReason"]
                            }
                            """;
                    RequestCategoryResponseDto createdCat = requestCategoryService.createCategory(
                            new CreateRequestCategoryDto("GENERAL", "General Request", "General requests", "file", schema, true)
                    );
                    categoryId = createdCat.id();
                }

                Map<String, Object> payload = new HashMap<>(args);
                payload.put("urgencyReason", "Submitted via Nexus AI Co-pilot");
                payload.put("ramGb", 16);
                payload.put("storage", "512GB");

                CreateRequestDto createDto = new CreateRequestDto(
                        categoryId,
                        null,
                        title,
                        "Request initiated through Nexus AI Co-pilot",
                        RequestPriority.valueOf(priorityStr.toUpperCase()),
                        payload,
                        true
                );

                RequestResponseDto created = requestService.createRequest(createDto, principal);

                action.setStatus(ToolExecutionStatus.SUCCEEDED);
                action.setConfirmedById(userId);
                action.setConfirmedAt(Instant.now());
                action.setResultSummary(objectMapper.writeValueAsString(Map.of(
                        "requestId", created.id(),
                        "requestNumber", created.requestNumber(),
                        "status", created.status()
                )));
                toolExecutionRepository.save(action);

                log.info("Action proposal {} successfully executed -> created request {}", actionId, created.requestNumber());
                return created;
            } else {
                throw new UnsupportedOperationException("Unknown tool action: " + action.getToolName());
            }
        } catch (Exception e) {
            log.error("Failed to execute action proposal {}: {}", actionId, e.getMessage(), e);
            action.setStatus(ToolExecutionStatus.FAILED);
            action.setErrorCode(e.getClass().getSimpleName());
            toolExecutionRepository.save(action);
            throw new RuntimeException("Action execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel an action proposal.
     */
    public void cancelAction(UserPrincipal principal, UUID actionId) {
        AiToolExecutionEntity action = toolExecutionRepository.findByOrganizationIdAndId(principal.getOrganizationId(), actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Action proposal not found"));

        if (!action.getUserId().equals(principal.getUserId())) {
            throw new AccessDeniedException("Not authorized to cancel this action.");
        }

        action.setStatus(ToolExecutionStatus.REJECTED);
        toolExecutionRepository.save(action);
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummaryDto> listConversations(UserPrincipal principal, Pageable pageable) {
        return conversationRepository.findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(
                principal.getOrganizationId(), principal.getUserId(), pageable)
                .map(c -> new ConversationSummaryDto(
                        c.getId(),
                        c.getTitle(),
                        c.getCreatedAt(),
                        c.getUpdatedAt(),
                        c.getMessages().size()
                ));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getConversationMessages(UserPrincipal principal, UUID conversationId) {
        AiConversationEntity conversation = conversationRepository.findByOrganizationIdAndId(
                principal.getOrganizationId(), conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!conversation.getUserId().equals(principal.getUserId())) {
            throw new AccessDeniedException("Not authorized to access this conversation.");
        }

        return messageRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
                principal.getOrganizationId(), conversationId).stream()
                .map(m -> new ChatMessageDto(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        deserializeCitations(m.getCitations()),
                        deserializeProposal(m.getToolCall()),
                        m.getCreatedAt()
                ))
                .toList();
    }

    private AiConversationEntity createConversation(UUID organizationId, UUID userId, String firstMessage) {
        String title = (firstMessage != null && !firstMessage.isBlank())
                ? (firstMessage.length() > 40 ? firstMessage.substring(0, 37) + "..." : firstMessage)
                : "New Conversation";
        AiConversationEntity conv = new AiConversationEntity(organizationId, userId, title);
        return conversationRepository.save(conv);
    }

    private String serializeCitations(List<SearchResultDto> citations) {
        if (citations == null || citations.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            return null;
        }
    }

    private List<SearchResultDto> deserializeCitations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, SearchResultDto.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializeProposal(ActionProposalDto proposal) {
        if (proposal == null) return null;
        try {
            return objectMapper.writeValueAsString(proposal);
        } catch (Exception e) {
            return null;
        }
    }

    private ActionProposalDto deserializeProposal(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ActionProposalDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractDepartmentCode(String message) {
        if (message == null) return "ALL";
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("engineering") || lower.contains("eng")) return "ENG";
        if (lower.contains("information technology") || lower.contains("it")) return "IT";
        if (lower.contains("human resources") || lower.contains("hr")) return "HR";
        if (lower.contains("finance") || lower.contains("fin")) return "FIN";
        if (lower.contains("operations") || lower.contains("ops")) return "OPS";
        return "ALL";
    }
}
