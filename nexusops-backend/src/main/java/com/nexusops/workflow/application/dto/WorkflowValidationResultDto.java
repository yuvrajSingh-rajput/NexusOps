package com.nexusops.workflow.application.dto;

import java.util.List;

public record WorkflowValidationResultDto(
        boolean valid,
        List<String> errors,
        List<String> warnings
) {}

