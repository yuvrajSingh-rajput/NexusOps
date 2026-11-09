package com.nexusops.knowledge.domain;

import org.springframework.modulith.NamedInterface;

@NamedInterface
public enum DocumentAccessScope {
    ALL,
    DEPARTMENT,
    ROLE
}

