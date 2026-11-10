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
import com.nexusops.shared.exception.NexusOpsException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
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
import java.util.Locale;
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

        // Find previous assistant message for conversational context
        String previousAssistantContext = null;
        if (dto.conversationId() != null) {
            List<AiMessageEntity> prevMessages = messageRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
                    organizationId, conversation.getId());
            for (int k = prevMessages.size() - 1; k >= 0; k--) {
                AiMessageEntity m = prevMessages.get(k);
                if (m.getRole() == AiMessageRole.ASSISTANT && !m.getId().equals(userMsg.getId())) {
                    previousAssistantContext = m.getContent();
                    break;
                }
            }
        }

        // 3. Classify Intent with contextual memory
        IntentClassifier.Intent intent = intentClassifier.classify(dto.message(), previousAssistantContext);
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
            case CONVERSATION_FOLLOW_UP -> {
                // Look up previous messages in the current conversation
                List<AiMessageEntity> history = messageRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
                        organizationId, conversation.getId());
                AiMessageEntity previousAssistantMsg = null;
                for (int j = history.size() - 1; j >= 0; j--) {
                    AiMessageEntity msg = history.get(j);
                    if (msg.getRole() == AiMessageRole.ASSISTANT && !msg.getId().equals(userMsg.getId())) {
                        previousAssistantMsg = msg;
                        break;
                    }
                }

                if (previousAssistantMsg != null && previousAssistantMsg.getContent() != null) {
                    reply = "Here is what I explained previously in this conversation:\n\n" +
                            "> " + previousAssistantMsg.getContent().replace("\n", "\n> ") + "\n\n" +
                            "Let me know if you would like me to clarify or look up anything specific!";
                } else {
                    reply = "I haven't explained anything in this conversation yet. Feel free to ask me about your organization's policies, request statuses, or pending approvals!";
                }
            }
            case INCOMPLETE_QUERY -> {
                reply = "It looks like your question was cut short! What policy or topic would you like to know about? For example:\n\n" +
                        "- **Leave Policy:** *\"What is our annual and sick leave policy?\"*\n" +
                        "- **Hardware Budget:** *\"What is our developer laptop refresh policy?\"*\n" +
                        "- **Expense Reimbursement:** *\"What is the meal and travel reimbursement limit?\"*\n" +
                        "- **Operational Queues:** *\"Show my submitted requests\"* or *\"Show pending approvals\"*.";
            }
            case POLICY_QA -> {
                // Grounded RAG Semantic Search
                citations = knowledgeService.semanticSearch(organizationId, dto.message(), 5, 0.25);
                if (!citations.isEmpty()) {
                    String systemPrompt = ragContextBuilder.buildPromptContext(citations, dto.message());
                    reply = aiModelProvider.generateAnswer(systemPrompt, dto.message(), citations);
                } else {
                    // Try fallback with broader similarity threshold
                    List<SearchResultDto> broadMatches = knowledgeService.semanticSearch(organizationId, dto.message(), 3, 0.15);
                    if (!broadMatches.isEmpty()) {
                        String systemPrompt = ragContextBuilder.buildPromptContext(broadMatches, dto.message());
                        reply = aiModelProvider.generateAnswer(systemPrompt, dto.message(), broadMatches);
                        citations = broadMatches;
                    } else {
                        reply = "I could not find specific policy documentation matching your inquiry: **\"" + dto.message() + "\"**.\n\n" +
                                "Here are the policy topics available in your organization's knowledge base:\n" +
                                "- **IT Hardware & Procurement:** Equipment standards, approval tiers ($10k/$50k), and refresh cycles.\n" +
                                "- **Leave & Absence:** Annual, sick, and parental leave policies.\n" +
                                "- **Employee Benefits & Expenses:** Health benefits and expense reimbursement rules.\n\n" +
                                "Please ask a question related to these topics, or contact HR / IT directly.";
                    }
                }
            }
            case CONVERSATION -> {
                String clean = dto.message().toLowerCase(Locale.ROOT).trim();
                if (clean.startsWith("thank") || clean.contains("thanks") || clean.equals("thx")) {
                    reply = "You're very welcome! Let me know if you need any other policy details, operational request tracking, or approval assistance.";
                } else if (clean.equals("ok") || clean.equals("okay") || clean.equals("cool") || clean.equals("got it") || clean.equals("understood") || clean.equals("great")) {
                    reply = "Understood! I'm here whenever you need help with company policies, workflow requests, or tasks.";
                } else {
                    reply = "Hello! I am **Nexus AI**, your enterprise operations co-pilot.\n\n" +
                            "Here is what I can assist you with:\n" +
                            "- **Policy Inquiries (RAG):** Ask questions about hardware budgets, leaves, or reimbursement rules.\n" +
                            "- **Track Requests:** Ask *\"What is the status of my requests?\"*\n" +
                            "- **Approvals & Tasks:** Ask *\"Show my pending approvals\"* or *\"Show my tasks\"*.\n" +
                            "- **Analytics & Reports:** Ask *\"Generate department report for Engineering\"* or *\"Show operational metrics\"*.\n" +
                            "- **Create Requests:** Ask *\"I need a new developer laptop\"* to prepare a submission card.";
                }
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
                .replaceAll("(?i)^(please |kindly )?(i need|i want|i would like|i'd like|can i get|can you (please )?(create|generate|prepare|request)|please (create|request|order)|create (a |an )?(request for )?|create |raise (a |an )?(request for )?|raise |file (a |an )?(request for )?|file |claim (a |an )?|make (a |an )?(request for )?|make |submit (a |an )?(request for )?|submit |apply (for a |for an |for )?|request (for a |for an |for |a |an )?|request |order (a |an |me a |me )?|order |get me (a |an )?|procure (a |an )?)\\s*", "")
                .replaceAll("(?i)\\s+(for me|please|reuest|request|ticket|claim)$", "")
                .trim();
        if (cleanTitle.isBlank() || cleanTitle.equalsIgnoreCase("request") || cleanTitle.equalsIgnoreCase("reuest")) {
            cleanTitle = "Expense Reimbursement Request";
        }
        cleanTitle = Character.toUpperCase(cleanTitle.charAt(0)) + cleanTitle.substring(1);

        String lower = prompt.toLowerCase(Locale.ROOT);
        String categoryCode = "DEVELOPER_LAPTOP";
        if (lower.contains("software") || lower.contains("license") || lower.contains("access") || lower.contains("tool") || lower.contains("seat") || lower.contains("copilot") || lower.contains("jetbrains") || lower.contains("figma") || lower.contains("aws")) {
            categoryCode = "SOFTWARE_ACCESS";
        } else if (lower.contains("reimburse") || lower.contains("expense") || lower.contains("expence") || lower.contains("expences") || lower.contains("travel") || lower.contains("meal") || lower.contains("flight") || lower.contains("recharge") || lower.contains("hotel") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("receipt")) {
            categoryCode = "EXPENSE_REIMBURSEMENT";
        }

        Map<String, Object> params = new HashMap<>();
        params.put("title", cleanTitle);
        params.put("priority", "MEDIUM");
        params.put("categoryCode", categoryCode);

        if ("DEVELOPER_LAPTOP".equals(categoryCode)) {
            String deviceModel = "MacBook Pro 16 M3 Max";
            if (lower.contains("dell") || lower.contains("xps")) {
                deviceModel = "Dell XPS 15 9530";
            } else if (lower.contains("thinkpad") || lower.contains("lenovo")) {
                deviceModel = "Lenovo ThinkPad P1 Gen 6";
            } else if (lower.contains("framework")) {
                deviceModel = "Framework Laptop 16";
            }
            int ramGb = 64;
            if (lower.contains("128")) ramGb = 128;
            else if (lower.contains("32")) ramGb = 32;

            params.put("deviceModel", deviceModel);
            params.put("ramGb", ramGb);
            params.put("estimatedAmount", 3500.00);
            params.put("urgencyReason", "Requested via Nexus AI Co-pilot for development workstation setup.");
        } else if ("SOFTWARE_ACCESS".equals(categoryCode)) {
            String softwareName = "GitHub Copilot Enterprise";
            if (lower.contains("jetbrains") || lower.contains("intellij")) softwareName = "JetBrains All Products Pack";
            else if (lower.contains("aws")) softwareName = "AWS Dev Sandbox";
            else if (lower.contains("figma")) softwareName = "Figma Enterprise";

            params.put("softwareName", softwareName);
            params.put("licenseType", "Annual");
            params.put("estimatedAmount", 450.00);
            params.put("justification", "Requested via Nexus AI Co-pilot for engineering workflows.");
        } else {
            String expenseType = "Home Office Hardware";
            double estAmount = 250.00;
            if (lower.contains("travel") || lower.contains("flight") || lower.contains("hotel") || lower.contains("lodging")) {
                expenseType = "Travel & Lodging";
                estAmount = 450.00;
            } else if (lower.contains("conference") || lower.contains("training") || lower.contains("seminar")) {
                expenseType = "Conference & Training";
                estAmount = 600.00;
            } else if (lower.contains("lunch") || lower.contains("dinner") || lower.contains("catering") || lower.contains("food")) {
                expenseType = "Team Lunch & Catering";
                estAmount = 120.00;
            } else if (lower.contains("recharge") || lower.contains("mobile") || lower.contains("phone") || lower.contains("wifi") || lower.contains("internet")) {
                expenseType = "Home Office Hardware";
                estAmount = 50.00;
            }

            params.put("expenseType", expenseType);
            params.put("estimatedAmount", estAmount);
            params.put("expenseDate", java.time.LocalDate.now().toString());
            params.put("receiptNumber", "REC-" + (int) (Math.random() * 90000 + 10000));
            params.put("receiptDetails", "Receipt #" + (int) (Math.random() * 90000 + 10000) + " - Reimbursable operational expense");
        }

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
                String categoryCode = args.containsKey("categoryCode") 
                        ? String.valueOf(args.get("categoryCode")).trim().toUpperCase() 
                        : "DEVELOPER_LAPTOP";

                // Lookup matching category for this organization
                UUID categoryId;
                Optional<RequestCategoryResponseDto> matchedCat = requestCategoryService.getCategoryByCode(organizationId, categoryCode);
                if (matchedCat.isPresent()) {
                    categoryId = matchedCat.get().id();
                } else {
                    List<RequestCategoryResponseDto> categories = requestCategoryService.getActiveCategories();
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
                }

                Map<String, Object> payload = new HashMap<>(args);
                payload.remove("title");
                payload.remove("priority");
                payload.remove("categoryCode");

                // Guarantee valid schema fields
                if ("DEVELOPER_LAPTOP".equals(categoryCode) || payload.containsKey("deviceModel")) {
                    payload.putIfAbsent("deviceModel", "MacBook Pro 16 M3 Max");
                    payload.putIfAbsent("ramGb", 64);
                    payload.putIfAbsent("estimatedAmount", 3500.00);
                    payload.putIfAbsent("urgencyReason", "Requested via Nexus AI Co-pilot for workstation setup.");
                } else if ("SOFTWARE_ACCESS".equals(categoryCode)) {
                    payload.putIfAbsent("softwareName", "GitHub Copilot Enterprise");
                    payload.putIfAbsent("licenseType", "Annual");
                    payload.putIfAbsent("estimatedAmount", 450.00);
                    payload.putIfAbsent("justification", "Requested via Nexus AI Co-pilot");
                } else if ("EXPENSE_REIMBURSEMENT".equals(categoryCode)) {
                    payload.putIfAbsent("expenseType", "Home Office Hardware");
                    payload.putIfAbsent("estimatedAmount", 250.00);
                    payload.putIfAbsent("expenseDate", java.time.LocalDate.now().toString());
                    if (payload.containsKey("receiptNumber") && !payload.containsKey("receiptDetails")) {
                        payload.put("receiptDetails", "Receipt #" + payload.get("receiptNumber"));
                    }
                    payload.putIfAbsent("receiptDetails", "Receipt #" + (int) (Math.random() * 90000 + 10000) + " - Reimbursable operational expense");
                }

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
        } catch (NexusOpsException | AccessDeniedException | IllegalStateException e) {
            log.warn("Validation error on action proposal {}: {}", actionId, e.getMessage());
            action.setStatus(ToolExecutionStatus.FAILED);
            action.setErrorCode(e.getClass().getSimpleName());
            toolExecutionRepository.save(action);
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute action proposal {}: {}", actionId, e.getMessage(), e);
            action.setStatus(ToolExecutionStatus.FAILED);
            action.setErrorCode(e.getClass().getSimpleName());
            toolExecutionRepository.save(action);
            throw new NexusOpsException("ACTION_FAILED", "Failed to execute action: " + e.getMessage());
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
