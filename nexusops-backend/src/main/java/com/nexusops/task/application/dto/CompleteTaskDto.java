package com.nexusops.task.application.dto;

import java.util.Map;

public record CompleteTaskDto(
        Map<String, Object> outputData,
        String notes
) {}

