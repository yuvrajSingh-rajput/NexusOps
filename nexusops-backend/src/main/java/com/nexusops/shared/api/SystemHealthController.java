package com.nexusops.shared.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "service", "NexusOps Backend",
                "version", "0.1.0-SNAPSHOT"
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}

