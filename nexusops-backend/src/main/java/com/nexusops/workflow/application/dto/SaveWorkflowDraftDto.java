package com.nexusops.workflow.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SaveWorkflowDraftDto(
        @NotEmpty List<@Valid SaveWorkflowNodeDto> nodes,
        List<@Valid SaveWorkflowTransitionDto> transitions
) {}

