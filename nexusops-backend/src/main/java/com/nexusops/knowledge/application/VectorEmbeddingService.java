package com.nexusops.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Service for generating and operating on 768-dimensional vector embeddings.
 * Supports local Ollama (nomic-embed-text) when running, with graceful deterministic
 * semantic hash projection fallback when Ollama is offline or unconfigured.
 */
@Service
public class VectorEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingService.class);

    private final String ollamaUrl;
    private final String modelName;
    private final int dimension;
    private final ObjectMapper objectMapper;

    public VectorEmbeddingService(
            @Value("${nexusops.ai.embedding.ollama-url:http://localhost:11434}") String ollamaUrl,
            @Value("${nexusops.ai.embedding.model:nomic-embed-text}") String modelName,
            @Value("${nexusops.ai.embedding.dimension:768}") int dimension) {
        this.ollamaUrl = ollamaUrl.endsWith("/") ? ollamaUrl.substring(0, ollamaUrl.length() - 1) : ollamaUrl;
        this.modelName = modelName;
        this.dimension = dimension;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a 768-dimensional normalized unit vector embedding for the input text.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        // Try local Ollama if reachable
        try {
            float[] ollamaVector = fetchOllamaEmbedding(text);
            if (ollamaVector != null && ollamaVector.length == dimension) {
                return normalize(ollamaVector);
            }
        } catch (Exception e) {
            log.trace("Ollama embedding endpoint unavailable ({}), using deterministic local projection", e.getMessage());
        }

        // Deterministic local semantic hash projection fallback
        return generateDeterministicVector(text);
    }

    private float[] fetchOllamaEmbedding(String text) {
        try {
            URL url = URI.create(ollamaUrl + "/api/embeddings").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);

            String payload = objectMapper.writeValueAsString(new OllamaEmbedRequest(modelName, text));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream is = conn.getInputStream()) {
                    JsonNode root = objectMapper.readTree(is);
                    JsonNode embeddingNode = root.get("embedding");
                    if (embeddingNode != null && embeddingNode.isArray()) {
                        float[] vec = new float[embeddingNode.size()];
                        for (int i = 0; i < embeddingNode.size(); i++) {
                            vec[i] = (float) embeddingNode.get(i).asDouble();
                        }
                        return vec;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back silently
        }
        return null;
    }

    /**
     * Generates a 768-dimensional normalized dense embedding vector using word and n-gram hash projections.
     * Ensures consistent semantic similarity between texts sharing vocabulary and key phrases.
     */
    public float[] generateDeterministicVector(String text) {
        float[] vector = new float[dimension];
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] words = normalized.split("\\s+");

        for (String word : words) {
            if (word.isBlank() || word.length() < 2) {
                continue;
            }

            // Word-level hash projection
            int wordHash = Math.abs(word.hashCode());
            int idx1 = wordHash % dimension;
            int idx2 = (wordHash / 31) % dimension;
            float weight = 1.0f / (float) Math.log(word.length() + 2);
            vector[idx1] += weight;
            vector[idx2] += (wordHash % 2 == 0 ? weight : -weight) * 0.75f;

            // Character 3-gram projections for partial/subword similarity
            if (word.length() >= 3) {
                for (int i = 0; i <= word.length() - 3; i++) {
                    String trigram = word.substring(i, i + 3);
                    int triHash = Math.abs(trigram.hashCode());
                    int triIdx = triHash % dimension;
                    vector[triIdx] += 0.35f;
                }
            }
        }

        return normalize(vector);
    }

    /**
     * Normalizes a vector to unit length (L2 norm = 1.0) so dot product equals cosine similarity.
     */
    public float[] normalize(float[] vector) {
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += (v * v);
        }
        double norm = Math.sqrt(sumSq);
        if (norm == 0.0) {
            return vector;
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    /**
     * Calculates cosine similarity between two vectors in [-1.0, 1.0].
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Formats vector as pgvector string representation: "[0.123, -0.456, ...]"
     */
    public String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parses pgvector string "[0.123, -0.456, ...]" into float[]
     */
    public float[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return new float[dimension];
        }
        String clean = vectorStr.trim();
        if (clean.startsWith("[")) {
            clean = clean.substring(1);
        }
        if (clean.endsWith("]")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        String[] parts = clean.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private record OllamaEmbedRequest(String model, String prompt) {}
}

