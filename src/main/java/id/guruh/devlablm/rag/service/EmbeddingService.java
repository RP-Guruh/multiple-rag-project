package id.guruh.devlablm.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AiSettingService aiSettingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingService(AiSettingService aiSettingService, ObjectMapper objectMapper) {
        this.aiSettingService = aiSettingService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Computes embeddings for multiple text chunks.
     * Returns a list of float arrays corresponding to each input chunk.
     */
    public List<float[]> computeEmbeddings(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        Optional<AiSetting> optAi = aiSettingService.load();
        String aiUrl = optAi.map(AiSetting::getAiUrl).orElse(null);
        String apiKey = optAi.map(aiSettingService::decryptPassword).orElse(null);
        String modelName = optAi.map(AiSetting::getModelName).orElse("nomic-embed-text");

        // 1. Try primary configured endpoint from AI Settings
        if (aiUrl != null && !aiUrl.isBlank()) {
            try {
                String cleanUrl = aiUrl.trim();
                boolean isOllama = cleanUrl.contains(":11434") || cleanUrl.contains("/api/embed");
                if (isOllama) {
                    return computeOllamaEmbeddings(cleanUrl, modelName, texts);
                } else {
                    return computeOpenAiEmbeddings(cleanUrl, apiKey, modelName, texts);
                }
            } catch (Exception ex) {
                log.warn("Primary AI endpoint failed for embeddings ({}). Falling back to local Ollama...", ex.getMessage());
            }
        }

        // 2. Fallback: Local Ollama (nomic-embed-text)
        log.info("Computing embeddings via fallback local Ollama (http://localhost:11434, nomic-embed-text)...");
        return computeOllamaEmbeddings("http://localhost:11434", "nomic-embed-text:latest", texts);
    }

    private List<float[]> computeOllamaEmbeddings(String baseAiUrl, String model, List<String> texts) throws Exception {
        // Ollama native embed API endpoint (/api/embed)
        String endpoint = baseAiUrl.contains("/api/embed") ? baseAiUrl :
                (baseAiUrl.endsWith("/") ? baseAiUrl + "api/embed" : baseAiUrl + "/api/embed");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Ollama embed API returned status " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode embeddingsNode = root.path("embeddings");
        List<float[]> results = new ArrayList<>();

        if (embeddingsNode.isArray()) {
            for (JsonNode vecNode : embeddingsNode) {
                results.add(parseVector(vecNode));
            }
        }

        return results;
    }

    private List<float[]> computeOpenAiEmbeddings(String baseAiUrl, String apiKey, String model, List<String> texts) throws Exception {
        // Standard OpenAI compatible embeddings endpoint (/v1/embeddings)
        String endpoint = baseAiUrl.contains("/embeddings") ? baseAiUrl :
                (baseAiUrl.endsWith("/") ? baseAiUrl + "v1/embeddings" : baseAiUrl + "/v1/embeddings");

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);

        String jsonPayload = objectMapper.writeValueAsString(body);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8));

        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("OpenAI embed API returned status " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode dataArray = root.path("data");
        List<float[]> results = new ArrayList<>();

        if (dataArray.isArray()) {
            for (JsonNode item : dataArray) {
                results.add(parseVector(item.path("embedding")));
            }
        }

        return results;
    }

    private float[] parseVector(JsonNode arrNode) {
        if (!arrNode.isArray()) return new float[0];
        float[] vec = new float[arrNode.size()];
        for (int i = 0; i < arrNode.size(); i++) {
            vec[i] = (float) arrNode.get(i).asDouble();
        }
        return vec;
    }
}
