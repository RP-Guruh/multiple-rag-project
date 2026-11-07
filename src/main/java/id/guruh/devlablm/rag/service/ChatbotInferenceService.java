package id.guruh.devlablm.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.sqlite.entity.AiSetting;
import id.guruh.devlablm.sqlite.service.AiSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ChatbotInferenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotInferenceService.class);

    private final RagRetrievalService ragRetrievalService;
    private final AiSettingService aiSettingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public record ChatResponse(String answer, List<RagRetrievalService.RetrievedContext> contexts) {}

    public ChatbotInferenceService(
            RagRetrievalService ragRetrievalService,
            AiSettingService aiSettingService,
            ObjectMapper objectMapper) {
        this.ragRetrievalService = ragRetrievalService;
        this.aiSettingService = aiSettingService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Executes RAG inference with strict project isolation.
     * Knowledge context is retrieved exclusively where project_id = project.getId().
     */
    public ChatResponse chat(ProjectEntity project, String userMessage) throws Exception {
        if (project == null || project.getId() == null) {
            throw new IllegalArgumentException("A valid project must be selected before chatting.");
        }

        // 1. Strict isolated RAG context retrieval
        List<RagRetrievalService.RetrievedContext> contexts =
                ragRetrievalService.retrieveContext(project.getId(), userMessage, 4, 0.25);

        // 2. Strict grounding & guardrail prompt: Refuse answering outside project knowledge
        StringBuilder systemInstruction = new StringBuilder();
        systemInstruction.append("""
            CRITICAL INSTRUCTIONS & STRICT BOUNDARIES:
            1. You are a specialized assistant dedicated EXCLUSIVELY to the project: "%s".
            2. You must answer the user query STRICTLY and ONLY using the provided Knowledge Base Context below.
            3. If the provided context does not contain the answer, or if the question is unrelated to the context, you MUST politely refuse to answer. State clearly:
               "Maaf, informasi tersebut tidak ditemukan dalam basis pengetahuan (knowledge base) dokumen proyek ini."
            4. NEVER use pre-trained external knowledge, assumptions, or general knowledge to answer questions that are not supported by the context.
            5. Do NOT answer general trivia, coding questions, or unrelated topics unless they are explicitly present in the context.
            
            """.formatted(project.getName()));

        if (project.getSystemPrompt() != null && !project.getSystemPrompt().isBlank()) {
            systemInstruction.append("PROJECT SPECIFIC PERSONA & RULES:\n")
                    .append(project.getSystemPrompt().trim())
                    .append("\n\n");
        }

        if (!contexts.isEmpty()) {
            systemInstruction.append("--- VERIFIED PROJECT KNOWLEDGE BASE CONTEXT ---\n");
            for (int i = 0; i < contexts.size(); i++) {
                RagRetrievalService.RetrievedContext ctx = contexts.get(i);
                systemInstruction.append(String.format("[Citation Chunk #%d (Confidence: %.0f%%)]:\n%s\n\n",
                        i + 1, ctx.similarity() * 100, ctx.content().trim()));
            }
            systemInstruction.append("--- END OF KNOWLEDGE BASE CONTEXT ---\n");
        } else {
            systemInstruction.append("""
                --- NOTICE ---
                NO MATCHING DOCUMENTS FOUND in the knowledge base for this query.
                You MUST immediately inform the user that no relevant information exists in this project's documents.
                """);
        }

        // 3. Dispatch to AI Chat model
        String answer = callChatModel(systemInstruction.toString(), userMessage);
        return new ChatResponse(answer, contexts);
    }

    private String callChatModel(String systemPrompt, String userMessage) throws Exception {
        Optional<AiSetting> optAi = aiSettingService.load();
        if (optAi.isEmpty()) {
            throw new IllegalStateException("AI settings not configured. Please configure AI Settings.");
        }

        AiSetting ai = optAi.get();
        String aiUrl = ai.getAiUrl();
        String apiKey = aiSettingService.decryptPassword(ai);
        String modelName = (ai.getModelName() != null && !ai.getModelName().isBlank())
                ? ai.getModelName().trim()
                : "dian_sipemikir";

        if (aiUrl == null || aiUrl.isBlank()) {
            throw new IllegalStateException("AI URL is empty. Please configure AI Settings.");
        }

        String cleanUrl = aiUrl.trim();
        boolean isOllama = cleanUrl.contains(":11434") || cleanUrl.endsWith("/api/chat");

        if (isOllama) {
            String endpoint = cleanUrl.endsWith("/api/chat") ? cleanUrl :
                    (cleanUrl.endsWith("/") ? cleanUrl + "api/chat" : cleanUrl + "/api/chat");

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            return root.path("message").path("content").asText("No response received.");
        } else {
            // OpenAI-compatible /v1/chat/completions
            String endpoint = cleanUrl.endsWith("/chat/completions") ? cleanUrl :
                    (cleanUrl.endsWith("/") ? cleanUrl + "v1/chat/completions" : cleanUrl + "/v1/chat/completions");

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));

            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body().trim();

            // Handle standard JSON response
            if (respBody.startsWith("{")) {
                JsonNode root = objectMapper.readTree(respBody);
                if (root.has("error")) {
                    throw new RuntimeException(root.path("error").path("message").asText());
                }
                return root.path("choices").path(0).path("message").path("content").asText("No response received.");
            }

            // Handle SSE streaming data fallback
            StringBuilder sseAccumulator = new StringBuilder();
            for (String line : respBody.split("\n")) {
                line = line.trim();
                if (line.startsWith("data:") && !line.contains("[DONE]")) {
                    try {
                        JsonNode chunk = objectMapper.readTree(line.substring(5).trim());
                        String delta = chunk.path("choices").path(0).path("delta").path("content").asText("");
                        sseAccumulator.append(delta);
                    } catch (Exception ignored) {}
                }
            }

            if (!sseAccumulator.isEmpty()) {
                return sseAccumulator.toString();
            }

            return respBody;
        }
    }
}
