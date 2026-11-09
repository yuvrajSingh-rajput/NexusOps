package com.nexusops.ai.application;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class IntentClassifier {

    public enum Intent {
        SEARCH_REQUESTS,
        SEARCH_APPROVALS,
        SEARCH_TASKS,
        CREATE_REQUEST,
        DEPARTMENT_REPORT,
        POLICY_QA,
        CONVERSATION
    }

    public Intent classify(String message) {
        if (message == null || message.isBlank()) {
            return Intent.CONVERSATION;
        }

        String lower = message.toLowerCase(Locale.ROOT).trim();

        // 1. Tool Intent: Department Analytics & Reports
        if (lower.contains("department report") || lower.contains("department metrics") || lower.contains("operations report")
                || lower.contains("analytics report") || lower.contains("sla report") || lower.contains("metrics report")
                || lower.contains("throughput") || (lower.contains("report") && (lower.contains("engineering") || lower.contains("eng") || lower.contains("hr") || lower.contains("it")))) {
            return Intent.DEPARTMENT_REPORT;
        }

        // 2. Tool Intent: Search Requests
        if (lower.contains("my request") || lower.contains("my submitted") || lower.contains("track request")
                || (lower.contains("status") && lower.contains("request") && !lower.contains("how"))
                || lower.contains("show request") || lower.contains("show all request") || lower.contains("list request")
                || lower.contains("view request") || lower.contains("get request")) {
            return Intent.SEARCH_REQUESTS;
        }

        // 2. Tool Intent: Search Approvals
        if (lower.contains("pending approval") || lower.contains("to approve") || lower.contains("my approvals")
                || lower.contains("waiting for my approval") || lower.contains("show approval") || lower.contains("list approval")) {
            return Intent.SEARCH_APPROVALS;
        }

        // 3. Tool Intent: Search Tasks
        if (lower.contains("my task") || lower.contains("assigned task") || lower.contains("tasks for me")
                || lower.contains("fulfillment task") || lower.contains("show task") || lower.contains("list task")) {
            return Intent.SEARCH_TASKS;
        }

        // 4. Tool Intent: Create Request Proposal
        if ((lower.startsWith("i need") || lower.startsWith("request a") || lower.startsWith("order a")
                || lower.contains("submit request") || lower.contains("create request") || lower.contains("new laptop request"))
                && !lower.contains("what is") && !lower.contains("policy")) {
            return Intent.CREATE_REQUEST;
        }

        // 5. Greetings / Help
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") || lower.contains("help")
                || lower.contains("what can you do") || lower.contains("who are you")) {
            return Intent.CONVERSATION;
        }

        // 6. Default to Grounded Policy QA (RAG)
        return Intent.POLICY_QA;
    }
}

