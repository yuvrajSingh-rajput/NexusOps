package com.nexusops.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.knowledge.application.dto.SearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AiModelProvider {

    private static final Logger log = LoggerFactory.getLogger(AiModelProvider.class);

    private final String ollamaUrl;
    private final String chatModel;
    private final ObjectMapper objectMapper;

    public AiModelProvider(
            @Value("${nexusops.ai.chat.ollama-url:http://localhost:11434}") String ollamaUrl,
            @Value("${nexusops.ai.chat.model:llama3.2}") String chatModel) {
        this.ollamaUrl = ollamaUrl.endsWith("/") ? ollamaUrl.substring(0, ollamaUrl.length() - 1) : ollamaUrl;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate response either via local Ollama (if available) or via grounded template synthesizer.
     */
    public String generateAnswer(String systemPrompt, String userMessage, List<SearchResultDto> citations) {
        // 1. Try local Ollama if reachable
        try {
            String ollamaReply = callOllama(systemPrompt, userMessage);
            if (ollamaReply != null && !ollamaReply.isBlank()) {
                return ollamaReply;
            }
        } catch (Exception e) {
            log.trace("Ollama chat endpoint not reachable ({}), utilizing grounded local synthesizer", e.getMessage());
        }

        // 2. Deterministic Grounded Policy Synthesizer (Zero-hallucination fallback)
        if (citations == null || citations.isEmpty()) {
            return "I cannot find an answer in your company's policy documents. Please contact HR or IT for assistance.";
        }

        SearchResultDto topMatch = citations.get(0);
        return String.format("**Source:** %s\n\n%s",
                topMatch.documentTitle(), topMatch.content());
    }

    private String callOllama(String systemPrompt, String userMessage) {
        try {
            URL url = URI.create(ollamaUrl + "/api/generate").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String requestBody = objectMapper.writeValueAsString(new OllamaGenerateRequest(
                    chatModel,
                    systemPrompt + "\n\nUser: " + userMessage,
                    false
            ));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    JsonNode responseNode = root.get("response");
                    if (responseNode != null) {
                        return responseNode.asText();
                    }
                }
            }
        } catch (Exception ignored) {
            // Silently fall back
        }
        return null;
    }

    private record OllamaGenerateRequest(String model, String prompt, boolean stream) {}
}

