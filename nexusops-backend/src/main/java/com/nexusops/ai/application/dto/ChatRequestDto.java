package com.nexusops.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatRequestDto(
        UUID conversationId,
        @NotBlank(message = "Message cannot be blank")
        String message
) {}

