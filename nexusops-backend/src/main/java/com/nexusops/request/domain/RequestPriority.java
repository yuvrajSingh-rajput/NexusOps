package com.nexusops.request.domain;

import org.springframework.modulith.NamedInterface;

@NamedInterface
public enum RequestPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

