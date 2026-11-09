package com.nexusops.knowledge.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * Uses Apache PDFBox for enterprise-grade PDF parsing.
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
            if (lowerName.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(mimeType)) {
                pages.addAll(extractPdfText(inputStream, fileName));
            } else {
                pages.addAll(extractPlainText(inputStream));
            }
        } catch (Exception e) {
            log.warn("Text extraction encountered an issue with document {}: {}. Using graceful fallback.", fileName, e.getMessage());
            pages.add(new ExtractedPage(1, "Document: " + fileName + "\nContent extracted with fallback handler."));
        }

        if (pages.isEmpty()) {
            pages.add(new ExtractedPage(1, "Document: " + fileName));
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
        String text = content.toString().trim();
        if (!text.isBlank()) {
            pages.add(new ExtractedPage(1, text));
        }
        return pages;
    }

    /**
     * Extracts text from PDF using Apache PDFBox.
     */
    private List<ExtractedPage> extractPdfText(InputStream inputStream, String fileName) {
        List<ExtractedPage> pages = new ArrayList<>();
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = document.getNumberOfPages();
                for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String text = stripper.getText(document).trim();
                    if (!text.isBlank()) {
                        pages.add(new ExtractedPage(pageNum, text));
                    }
                }
            }
        } catch (Exception e) {
            log.error("PDFBox extraction failed for {}: {}", fileName, e.getMessage());
        }

        if (pages.isEmpty()) {
            pages.add(new ExtractedPage(1, "Document: " + fileName + " (PDF text content unavailable or scanned image)"));
        }
        return pages;
    }
}
