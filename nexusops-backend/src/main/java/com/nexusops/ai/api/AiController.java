package com.nexusops.ai.api;

import com.nexusops.ai.application.AiAssistantService;
import com.nexusops.ai.application.dto.ChatMessageDto;
import com.nexusops.ai.application.dto.ChatRequestDto;
import com.nexusops.ai.application.dto.ChatResponseDto;
import com.nexusops.ai.application.dto.ConfirmActionDto;
import com.nexusops.ai.application.dto.ConversationSummaryDto;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiAssistantService aiAssistantService;

    public AiController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponseDto>> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequestDto request) {

        ChatResponseDto response = aiAssistantService.chat(principal, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("message") String message,
            @RequestParam(value = "conversationId", required = false) UUID conversationId) {

        SseEmitter emitter = new SseEmitter(60_000L);

        CompletableFuture.runAsync(() -> {
            try {
                ChatResponseDto response = aiAssistantService.chat(
                        principal, new ChatRequestDto(conversationId, message));

                // Stream word-by-word
                String fullText = response.reply();
                String[] words = fullText.split("\\s+");
                for (String word : words) {
                    emitter.send(SseEmitter.event().name("token").data(word + " "));
                    Thread.sleep(15);
                }

                emitter.send(SseEmitter.event().name("complete").data(response));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<Page<ConversationSummaryDto>>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ConversationSummaryDto> conversations = aiAssistantService.listConversations(principal, pageable);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        List<ChatMessageDto> messages = aiAssistantService.getConversationMessages(principal, id);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/actions/{id}/confirm")
    public ResponseEntity<ApiResponse<RequestResponseDto>> confirmAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmActionDto dto) {

        ConfirmActionDto resolvedDto = (dto != null) ? dto : new ConfirmActionDto(true, "Confirmed via UI");
        RequestResponseDto result = aiAssistantService.confirmAction(principal, id, resolvedDto);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/actions/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        aiAssistantService.cancelAction(principal, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
