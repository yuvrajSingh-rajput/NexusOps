package com.nexusops.knowledge.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sliding-window document chunker.
 * Slices extracted document text into semantic chunks with overlap to preserve context across chunk boundaries.
 */
@Service
public class DocumentChunkerService {

    public static final int DEFAULT_CHUNK_SIZE_CHARS = 1800; // ~450-500 tokens
    public static final int DEFAULT_CHUNK_OVERLAP_CHARS = 180; // ~45-50 tokens (10% overlap)

    public record RawChunk(int chunkIndex, String content, Integer pageNumber, int tokenCount) {}

    public List<RawChunk> chunkPages(List<TextExtractionService.ExtractedPage> pages) {
        return chunkPages(pages, DEFAULT_CHUNK_SIZE_CHARS, DEFAULT_CHUNK_OVERLAP_CHARS);
    }

    public List<RawChunk> chunkPages(List<TextExtractionService.ExtractedPage> pages, int chunkSize, int overlap) {
        List<RawChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (TextExtractionService.ExtractedPage page : pages) {
            String text = page.text() != null ? page.text().trim() : "";
            if (text.isBlank()) {
                continue;
            }

            if (text.length() <= chunkSize) {
                int tokenEstimate = estimateTokens(text);
                chunks.add(new RawChunk(chunkIndex++, text, page.pageNumber(), tokenEstimate));
                continue;
            }

            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());

                // Try to break at paragraph or sentence boundary if not at end of text
                if (end < text.length()) {
                    int boundary = findNaturalBreakBoundary(text, start, end);
                    if (boundary > start + (chunkSize / 2)) {
                        end = boundary;
                    }
                }

                String slice = text.substring(start, end).trim();
                if (!slice.isBlank()) {
                    int tokenEstimate = estimateTokens(slice);
                    chunks.add(new RawChunk(chunkIndex++, slice, page.pageNumber(), tokenEstimate));
                }

                if (end >= text.length()) {
                    break;
                }

                start = Math.max(start + 1, end - overlap);
            }
        }

        return chunks;
    }

    private int findNaturalBreakBoundary(String text, int start, int end) {
        // Priority 1: Paragraph break (\n\n)
        int doubleNewline = text.lastIndexOf("\n\n", end);
        if (doubleNewline > start) {
            return doubleNewline + 2;
        }

        // Priority 2: Single newline (\n)
        int newline = text.lastIndexOf('\n', end);
        if (newline > start) {
            return newline + 1;
        }

        // Priority 3: Sentence break (. or ! or ?)
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }

        // Priority 4: Word boundary (space)
        int space = text.lastIndexOf(' ', end);
        if (space > start) {
            return space + 1;
        }

        return end;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Approximate rule: 1 token ~= 4 English characters
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}

