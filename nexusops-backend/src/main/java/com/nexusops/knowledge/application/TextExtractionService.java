package com.nexusops.knowledge.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

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
     * Extracts text from PDF bytes supporting both uncompressed text streams and FlateDecode streams.
     */
    private List<ExtractedPage> extractPdfText(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        List<ExtractedPage> pages = new ArrayList<>();
        List<String> contentBlocks = new ArrayList<>();

        // Locate and decompress stream blocks
        byte[] streamMarker = "stream".getBytes(StandardCharsets.US_ASCII);
        byte[] endstreamMarker = "endstream".getBytes(StandardCharsets.US_ASCII);
        int streamIdx = 0;

        while ((streamIdx = indexOf(bytes, streamMarker, streamIdx)) != -1) {
            int startData = streamIdx + streamMarker.length;
            if (startData < bytes.length && bytes[startData] == '\r') startData++;
            if (startData < bytes.length && bytes[startData] == '\n') startData++;

            int endData = indexOf(bytes, endstreamMarker, startData);
            if (endData != -1) {
                int actualEnd = endData;
                while (actualEnd > startData && (bytes[actualEnd - 1] == '\r' || bytes[actualEnd - 1] == '\n')) {
                    actualEnd--;
                }
                byte[] streamBytes = new byte[actualEnd - startData];
                System.arraycopy(bytes, startData, streamBytes, 0, streamBytes.length);

                try (ByteArrayInputStream bais = new ByteArrayInputStream(streamBytes);
                     InflaterInputStream inflater = new InflaterInputStream(bais);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = inflater.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    contentBlocks.add(baos.toString(StandardCharsets.ISO_8859_1));
                } catch (Exception ignored) {
                    contentBlocks.add(new String(streamBytes, StandardCharsets.ISO_8859_1));
                }
                streamIdx = endData + endstreamMarker.length;
            } else {
                streamIdx += streamMarker.length;
            }
        }

        // Also add the raw bytes string in case text is outside streams
        contentBlocks.add(new String(bytes, StandardCharsets.ISO_8859_1));

        StringBuilder pageBuilder = new StringBuilder();
        int pageIndex = 1;

        for (String block : contentBlocks) {
            int lastIndex = 0;
            while ((lastIndex = block.indexOf("BT", lastIndex)) != -1) {
                int endIndex = block.indexOf("ET", lastIndex);
                if (endIndex != -1) {
                    String btBlock = block.substring(lastIndex + 2, endIndex);
                    int pStart = 0;
                    while ((pStart = btBlock.indexOf('(', pStart)) != -1) {
                        int pEnd = btBlock.indexOf(')', pStart);
                        if (pEnd != -1) {
                            String snippet = btBlock.substring(pStart + 1, pEnd);
                            if (!snippet.isBlank()) {
                                pageBuilder.append(snippet).append(" ");
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

                if (pageBuilder.length() > 2500) {
                    pages.add(new ExtractedPage(pageIndex++, pageBuilder.toString().trim()));
                    pageBuilder.setLength(0);
                }
            }
        }

        if (pageBuilder.length() > 0 || pages.isEmpty()) {
            pages.add(new ExtractedPage(pageIndex, pageBuilder.toString().trim()));
        }

        return pages;
    }

    private static int indexOf(byte[] array, byte[] target, int start) {
        if (target.length == 0) return start;
        for (int i = start; i <= array.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}
