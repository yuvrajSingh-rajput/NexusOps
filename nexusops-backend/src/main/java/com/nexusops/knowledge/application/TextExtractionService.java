package com.nexusops.knowledge.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting structured text from uploaded documents (TXT, MD, PDF, JSON).
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    public record ExtractedPage(int pageNumber, String text) {}

    /**
     * Extracts text page by page from the given input stream based on MIME type and file name.
     */
    public List<ExtractedPage> extractText(InputStream inputStream, String fileName, String mimeType) {
        String lowerName = fileName.toLowerCase();
        List<ExtractedPage> pages = new ArrayList<>();

        try {
            if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json")
                    || "text/plain".equalsIgnoreCase(mimeType) || "text/markdown".equalsIgnoreCase(mimeType)) {
                pages.addAll(extractPlainText(inputStream));
            } else if (lowerName.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(mimeType)) {
                pages.addAll(extractPdfText(inputStream));
            } else {
                // Generic fallback: read as plain text
                pages.addAll(extractPlainText(inputStream));
            }
        } catch (Exception e) {
            log.error("Failed to extract text from document {}: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to extract text from document: " + fileName, e);
        }

        if (pages.isEmpty()) {
            pages.add(new ExtractedPage(1, ""));
        }
        return pages;
    }

    private List<ExtractedPage> extractPlainText(InputStream inputStream) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        pages.add(new ExtractedPage(1, content.toString().trim()));
        return pages;
    }

    /**
     * Extracts text from PDF bytes.
     * Uses text block discovery or lightweight tokenized parsing.
     */
    private List<ExtractedPage> extractPdfText(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        List<ExtractedPage> pages = new ArrayList<>();

        // Look for text streams in PDF object structures (BT ... ET blocks)
        StringBuilder pageBuilder = new StringBuilder();
        int pageIndex = 1;
        int lastIndex = 0;

        while ((lastIndex = raw.indexOf("BT", lastIndex)) != -1) {
            int endIndex = raw.indexOf("ET", lastIndex);
            if (endIndex != -1) {
                String block = raw.substring(lastIndex + 2, endIndex);
                // Extract string literals enclosed in parentheses: (text)
                int pStart = 0;
                while ((pStart = block.indexOf('(', pStart)) != -1) {
                    int pEnd = block.indexOf(')', pStart);
                    if (pEnd != -1) {
                        String textSnippet = block.substring(pStart + 1, pEnd);
                        if (!textSnippet.isBlank()) {
                            pageBuilder.append(textSnippet).append(" ");
                        }
                        pStart = pEnd + 1;
                    } else {
                        break;
                    }
                }
                lastIndex = endIndex + 2;
            } else {
                lastIndex += 2;
            }

            // Simple heuristic page split every 2000 characters
            if (pageBuilder.length() > 2500) {
                pages.add(new ExtractedPage(pageIndex++, pageBuilder.toString().trim()));
                pageBuilder.setLength(0);
            }
        }

        if (pageBuilder.length() > 0 || pages.isEmpty()) {
            pages.add(new ExtractedPage(pageIndex, pageBuilder.toString().trim()));
        }

        return pages;
    }
}

