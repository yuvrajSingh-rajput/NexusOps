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
        CONVERSATION_FOLLOW_UP,
        INCOMPLETE_QUERY,
        CONVERSATION
    }

    public Intent classify(String message) {
        return classify(message, null);
    }

    public Intent classify(String message, String previousContext) {
        if (message == null || message.isBlank()) {
            return Intent.CONVERSATION;
        }

        String lower = message.toLowerCase(Locale.ROOT).trim();
        String prevLower = (previousContext != null) ? previousContext.toLowerCase(Locale.ROOT).trim() : "";

        // 1. Incomplete / Truncated Queries (e.g. "what is", "how do", "where is", single short words)
        if (lower.equals("what is") || lower.equals("what") || lower.equals("how do")
                || lower.equals("can you") || lower.equals("where is") || lower.equals("why")
                || (lower.length() <= 8 && (lower.startsWith("what") || lower.startsWith("how") || lower.startsWith("why")))) {
            return Intent.INCOMPLETE_QUERY;
        }

        // 2. Follow-Up Questions referencing previous assistant answer
        if (lower.contains("what did you said") || lower.contains("what did you say")
                || lower.contains("what did you explain") || lower.contains("what did you mean")
                || lower.contains("repeat that") || lower.contains("can you repeat")
                || lower.equals("repeat") || lower.contains("say that again")
                || lower.contains("what was that") || lower.contains("summarize what you said")
                || lower.contains("tell me more about what you said")) {
            return Intent.CONVERSATION_FOLLOW_UP;
        }

        // 3. Conversational Greetings, Thanks, Acknowledgments
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") || lower.equals("help")
                || lower.contains("what can you do") || lower.contains("who are you") || lower.contains("what is nexusops")
                || lower.startsWith("thank") || lower.contains("thanks") || lower.equals("thx")
                || lower.equals("ok") || lower.equals("okay") || lower.equals("cool")
                || lower.equals("got it") || lower.equals("great") || lower.equals("nice")
                || lower.equals("understood") || lower.equals("sure")) {
            return Intent.CONVERSATION;
        }

        // 4. Explicit Policy Inquiries (Prioritized if explicitly asking for rules, policies, or SLAs)
        boolean isExplicitPolicyQuery = lower.contains("what is the policy") || lower.contains("what is our policy")
                || lower.contains("company policy") || lower.contains("hr policy") || lower.contains("leave policy")
                || lower.contains("reimbursement policy") || lower.contains("hardware policy") || lower.contains("security policy")
                || lower.contains("approval tiers") || lower.contains("refresh cycle") || lower.contains("refresh cadence")
                || lower.contains("how does approval work") || lower.contains("what are the rules")
                || (lower.startsWith("what is the ") && !lower.contains("status"))
                || lower.contains("how many days") || lower.contains("coverage") || lower.contains("allowance");

        // 5. Tool Intent: Department Analytics & Reports
        if (lower.contains("department report") || lower.contains("department metrics") || lower.contains("operations report")
                || lower.contains("analytics report") || lower.contains("sla report") || lower.contains("metrics report")
                || lower.contains("throughput") || (lower.contains("report") && (lower.contains("engineering") || lower.contains("eng") || lower.contains("hr") || lower.contains("it")))) {
            return Intent.DEPARTMENT_REPORT;
        }

        // 6. Tool Intent: Search Requests
        if (lower.contains("my request") || lower.contains("my submitted") || lower.contains("track request")
                || (lower.contains("status") && lower.contains("request") && !lower.contains("how"))
                || lower.contains("show request") || lower.contains("show all request") || lower.contains("list request")
                || lower.contains("view request") || lower.contains("get request") || lower.contains("show requests")) {
            return Intent.SEARCH_REQUESTS;
        }

        // 7. Tool Intent: Search Approvals
        if (lower.contains("pending approval") || lower.contains("to approve") || lower.contains("my approvals")
                || lower.contains("waiting for my approval") || lower.contains("show approval") || lower.contains("list approval")
                || lower.contains("show approvals")) {
            return Intent.SEARCH_APPROVALS;
        }

        // 8. Tool Intent: Search Tasks
        if (lower.contains("my task") || lower.contains("assigned task") || lower.contains("tasks for me")
                || lower.contains("fulfillment task") || lower.contains("show task") || lower.contains("list task")
                || lower.contains("show tasks")) {
            return Intent.SEARCH_TASKS;
        }

        // 9. Tool Intent: Create Request Proposal
        // Match A: Direct Hardware Selection (e.g. user chooses a specific model like MacBook Pro, Dell XPS, etc.)
        boolean isHardwareModelSelection = lower.contains("macbook") || lower.contains("dell xps")
                || lower.contains("thinkpad") || lower.contains("framework laptop");

        // Match B: Request action verbs with requestable items
        boolean hasRequestVerb = lower.contains("request") || lower.contains("order") || lower.contains("need")
                || lower.contains("want") || lower.contains("apply") || lower.contains("procure")
                || lower.contains("purchase") || lower.contains("get me") || lower.contains("give me")
                || lower.contains("book") || lower.contains("issue") || lower.contains("submit");

        boolean hasRequestTarget = lower.contains("laptop") || lower.contains("computer") || lower.contains("workstation")
                || lower.contains("hardware") || lower.contains("macbook") || lower.contains("screen") || lower.contains("monitor")
                || lower.contains("software") || lower.contains("license") || lower.contains("access") || lower.contains("copilot")
                || lower.contains("jetbrains") || lower.contains("figma") || lower.contains("aws")
                || lower.contains("reimbursement") || lower.contains("expense") || lower.contains("flight") || lower.contains("hotel");

        // Match C: Follow-up selection after previous hardware policy discussion
        boolean isFollowUpDeviceSelection = !prevLower.isEmpty() && (prevLower.contains("hardware") || prevLower.contains("laptop") || prevLower.contains("workstation"))
                && (lower.contains("apple") || lower.contains("mac") || lower.contains("dell") || lower.contains("thinkpad") || lower.contains("framework") || lower.contains("first option") || lower.contains("second option"));

        if (!isExplicitPolicyQuery) {
            if (isHardwareModelSelection || (hasRequestVerb && hasRequestTarget) || isFollowUpDeviceSelection) {
                return Intent.CREATE_REQUEST;
            }
        }

        // 10. Default to Grounded Policy QA (RAG)
        return Intent.POLICY_QA;
    }
}



