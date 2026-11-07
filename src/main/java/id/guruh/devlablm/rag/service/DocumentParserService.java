package id.guruh.devlablm.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.guruh.devlablm.sqlite.entity.AiSetting;
import id.guruh.devlablm.sqlite.service.AiSettingService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);
    private static final int SCANNED_PDF_CHAR_THRESHOLD = 50;

    private final AiSettingService aiSettingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DocumentParserService(AiSettingService aiSettingService, ObjectMapper objectMapper) {
        this.aiSettingService = aiSettingService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Extracts text from document file based on extension.
     * Automatically falls back to Vision LLM if a PDF is detected as scanned images.
     */
    public String extractText(File file, String fileType) throws Exception {
        String ext = fileType == null ? "" : fileType.trim().toLowerCase();

        return switch (ext) {
            case "pdf" -> extractPdfWithVisionFallback(file);
            case "docx", "doc" -> extractWord(file);
            case "xlsx", "xls" -> extractExcel(file);
            case "txt", "md", "csv" -> Files.readString(file.toPath(), StandardCharsets.UTF_8);
            default -> {
                log.warn("Unknown format '{}', falling back to plain text read: {}", ext, file.getName());
                yield Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
        };
    }

    private String extractPdfWithVisionFallback(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String directText = stripper.getText(document);

            // If text layer exists and has substantial characters, return directly
            if (directText != null && directText.strip().length() > SCANNED_PDF_CHAR_THRESHOLD) {
                log.info("PDF '{}' contains digital text layer ({} chars).", file.getName(), directText.strip().length());
                return directText.strip();
            }

            log.info("PDF '{}' appears to be scanned or image-based (< {} chars). Triggering Multimodal Vision OCR...",
                    file.getName(), SCANNED_PDF_CHAR_THRESHOLD);

            return extractScannedPdfUsingVision(document);
        }
    }

    private String extractScannedPdfUsingVision(PDDocument document) throws Exception {
        Optional<AiSetting> optAi = aiSettingService.load();
        if (optAi.isEmpty()) {
            throw new IllegalStateException("AI settings not configured. Cannot perform Vision OCR on scanned PDF.");
        }

        AiSetting ai = optAi.get();
        String aiUrl = ai.getAiUrl();
        String apiKey = aiSettingService.decryptPassword(ai);
        String modelName = ai.getModelName();

        if (aiUrl == null || aiUrl.isBlank()) {
            throw new IllegalStateException("AI URL is empty. Please configure AI Settings for Vision OCR.");
        }

        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();
        StringBuilder combinedText = new StringBuilder();

        log.info("Processing {} pages via Multimodal Vision model '{}'...", pageCount, modelName);

        for (int i = 0; i < pageCount; i++) {
            // Render page at 150 DPI (good balance of OCR accuracy and payload size)
            BufferedImage bim = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bim, "jpeg", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            log.info("Invoking Vision OCR for page {} of {}...", (i + 1), pageCount);
            String pageText = callVisionApi(aiUrl, apiKey, modelName, base64Image, i + 1);
            if (!pageText.isBlank()) {
                combinedText.append("\n--- [Page ").append(i + 1).append("] ---\n");
                combinedText.append(pageText).append("\n");
            }
        }

        return combinedText.toString().strip();
    }

    /**
     * Dispatches request to either OpenAI-compatible chat completions or Ollama generate API.
     */
    private String callVisionApi(String aiUrl, String apiKey, String modelName, String base64Image, int pageNum) throws Exception {
        String cleanUrl = aiUrl.trim();
        boolean isOllama = cleanUrl.contains(":11434") || cleanUrl.endsWith("/api/generate");

        String requestJson;
        URI requestUri;

        if (isOllama) {
            String target = cleanUrl.endsWith("/api/generate") ? cleanUrl : cleanUrl + "/api/generate";
            requestUri = URI.create(target);

            Map<String, Object> body = new HashMap<>();
            body.put("model", (modelName != null && !modelName.isBlank()) ? modelName : "llava");
            body.put("prompt", "Extract and transcribe all text, numbers, and tabular data from this document image verbatim. Return only the extracted text.");
            body.put("stream", false);
            body.put("images", List.of(base64Image));

            requestJson = objectMapper.writeValueAsString(body);
        } else {
            // OpenAI compatible (/v1/chat/completions)
            String target = cleanUrl.endsWith("/chat/completions") ? cleanUrl :
                    (cleanUrl.endsWith("/") ? cleanUrl + "v1/chat/completions" : cleanUrl + "/v1/chat/completions");
            requestUri = URI.create(target);

            Map<String, Object> textPart = Map.of(
                    "type", "text",
                    "text", "Transcribe all printed or handwritten text and table content from this document page verbatim. Do not explain, output only the transcription."
            );
            Map<String, Object> imagePart = Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
            );

            Map<String, Object> userMsg = Map.of(
                    "role", "user",
                    "content", List.of(textPart, imagePart)
            );

            Map<String, Object> body = new HashMap<>();
            body.put("model", (modelName != null && !modelName.isBlank()) ? modelName : "gpt-4o-mini");
            body.put("messages", List.of(userMsg));
            body.put("temperature", 0.0);

            requestJson = objectMapper.writeValueAsString(body);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));

        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Vision API HTTP error " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        if (isOllama) {
            return root.path("response").asText("");
        } else {
            return root.path("choices").path(0).path("message").path("content").asText("");
        }
    }

    private String extractWord(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument docx = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : docx.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String extractExcel(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();

            for (Sheet sheet : workbook) {
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    sb.append(String.join("\t", cells)).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }
}
