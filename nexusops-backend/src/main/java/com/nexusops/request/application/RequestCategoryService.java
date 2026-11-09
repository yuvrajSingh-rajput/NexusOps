package com.nexusops.request.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.request.domain.RequestCategoryEntity;
import com.nexusops.request.infrastructure.RequestCategoryRepository;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RequestCategoryService {

    private final RequestCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public RequestCategoryService(RequestCategoryRepository categoryRepository, ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RequestCategoryResponseDto> getActiveCategories() {
        UUID orgId = TenantContextHolder.requireTenantId();
        List<RequestCategoryEntity> categories = categoryRepository.findAllByOrganizationIdAndActiveTrue(orgId);
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RequestCategoryEntity getEntityById(UUID id) {
        UUID orgId = TenantContextHolder.requireTenantId();
        return categoryRepository.findByOrganizationIdAndId(orgId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Request category not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public RequestCategoryResponseDto getCategoryById(UUID id) {
        return toDto(getEntityById(id));
    }

    @Transactional
    public RequestCategoryResponseDto createCategory(CreateRequestCategoryDto dto) {
        UUID orgId = TenantContextHolder.requireTenantId();
        String code = dto.code().trim().toUpperCase();

        if (categoryRepository.existsByOrganizationIdAndCode(orgId, code)) {
            throw new ConflictException("Category with code '" + code + "' already exists in this organization.");
        }

        // Validate JSON Schema syntax
        validateSchemaJson(dto.payloadSchema());

        RequestCategoryEntity entity = new RequestCategoryEntity(
                orgId,
                code,
                dto.name().trim(),
                dto.description(),
                dto.icon(),
                dto.payloadSchema(),
                dto.requiresWorkflow()
        );

        return toDto(categoryRepository.save(entity));
    }

    @Transactional
    public void seedDefaultCategoriesForTenant(UUID orgId) {
        if (!categoryRepository.findAllByOrganizationId(orgId).isEmpty()) {
            return;
        }

        List<RequestCategoryEntity> defaults = List.of(
                new RequestCategoryEntity(
                        orgId,
                        "DEVELOPER_LAPTOP",
                        "Developer Workstation & Laptop",
                        "High-performance workstations, MacBooks, and hardware displays for engineering personnel.",
                        "laptop",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "deviceModel": {
                              "type": "string",
                              "title": "Device Model",
                              "enum": ["MacBook Pro 16 M3 Max", "Dell XPS 15 9530", "Lenovo ThinkPad P1 Gen 6", "Framework Laptop 16"]
                            },
                            "ramGb": {
                              "type": "integer",
                              "title": "RAM (GB)",
                              "enum": [32, 64, 128]
                            },
                            "estimatedAmount": {
                              "type": "number",
                              "title": "Estimated Amount ($)",
                              "minimum": 500
                            },
                            "urgencyReason": {
                              "type": "string",
                              "title": "Business Justification"
                            }
                          },
                          "required": ["deviceModel", "ramGb", "estimatedAmount", "urgencyReason"]
                        }
                        """,
                        true
                ),
                new RequestCategoryEntity(
                        orgId,
                        "SOFTWARE_ACCESS",
                        "Software & Cloud Licenses",
                        "Developer tooling, cloud sandboxes, enterprise IDE licenses, and SaaS workspace seats.",
                        "key",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "softwareName": {
                              "type": "string",
                              "title": "Software Tool",
                              "enum": ["JetBrains All Products Pack", "GitHub Copilot Enterprise", "AWS Dev Sandbox", "Figma Enterprise", "Datadog APM"]
                            },
                            "licenseType": {
                              "type": "string",
                              "title": "License Term",
                              "enum": ["Annual", "Monthly"]
                            },
                            "estimatedAmount": {
                              "type": "number",
                              "title": "Estimated Cost ($)",
                              "minimum": 1
                            },
                            "justification": {
                              "type": "string",
                              "title": "Justification"
                            }
                          },
                          "required": ["softwareName", "licenseType", "estimatedAmount"]
                        }
                        """,
                        true
                ),
                new RequestCategoryEntity(
                        orgId,
                        "EXPENSE_REIMBURSEMENT",
                        "Travel & Expense Reimbursement",
                        "Corporate travel, client dinners, conference registrations, and home office supply receipts.",
                        "receipt",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "expenseType": {
                              "type": "string",
                              "title": "Expense Type",
                              "enum": ["Conference & Training", "Travel & Lodging", "Team Lunch & Catering", "Home Office Hardware"]
                            },
                            "estimatedAmount": {
                              "type": "number",
                              "title": "Amount ($)",
                              "minimum": 1
                            },
                            "expenseDate": {
                              "type": "string",
                              "title": "Date of Expense"
                            },
                            "receiptDetails": {
                              "type": "string",
                              "title": "Vendor & Receipt Notes"
                            }
                          },
                          "required": ["expenseType", "estimatedAmount", "expenseDate"]
                        }
                        """,
                        true
                )
        );

        categoryRepository.saveAll(defaults);
    }

    public void validatePayloadAgainstSchema(String schemaJson, Map<String, Object> payload) {
        if (schemaJson == null || schemaJson.isBlank() || schemaJson.trim().equals("{}")) {
            return;
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            JsonNode requiredNode = schemaNode.get("required");

            if (requiredNode != null && requiredNode.isArray()) {
                List<String> missingFields = new ArrayList<>();
                for (JsonNode field : requiredNode) {
                    String fieldName = field.asText();
                    Object val = payload.get(fieldName);
                    if (val == null || (val instanceof String s && s.isBlank())) {
                        missingFields.add(fieldName);
                    }
                }

                if (!missingFields.isEmpty()) {
                    throw new ValidationException("Required form fields are missing: " + String.join(", ", missingFields));
                }
            }
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid category schema snapshot.");
        }
    }

    private void validateSchemaJson(String schemaJson) {
        try {
            objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            throw new ValidationException("Payload schema must be valid JSON: " + e.getMessage());
        }
    }

    public RequestCategoryResponseDto toDto(RequestCategoryEntity entity) {
        return new RequestCategoryResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getIcon(),
                entity.getPayloadSchema(),
                entity.getSchemaVersion(),
                entity.isRequiresWorkflow(),
                entity.isActive()
        );
    }
}

