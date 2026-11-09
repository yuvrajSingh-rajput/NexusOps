package com.nexusops.shared.api;

public record ErrorDetail(
        String field,
        String issue
) {
    public static ErrorDetail of(String field, String issue) {
        return new ErrorDetail(field, issue);
    }
}
