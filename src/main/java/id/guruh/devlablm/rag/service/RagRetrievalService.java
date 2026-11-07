package id.guruh.devlablm.rag.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

@Service
public class RagRetrievalService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public record RetrievedContext(Long chunkId, Long documentId, String content, double similarity) {}

    public RagRetrievalService(
            @Qualifier("postgresDataSource") DataSource dataSource,
            EmbeddingService embeddingService) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.embeddingService = embeddingService;
    }

    /**
     * Retrieves the most relevant knowledge base chunks for a user query within a project.
     * Consumed directly by the AI chatbot service.
     */
    public List<RetrievedContext> retrieveContext(Long projectId, String query, int topK, double minSimilarity) throws Exception {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 1. Generate query vector
        List<float[]> queryEmbeddings = embeddingService.computeEmbeddings(List.of(query));
        if (queryEmbeddings.isEmpty() || queryEmbeddings.get(0).length == 0) {
            return List.of();
        }

        String vectorStr = toVectorString(queryEmbeddings.get(0));

        // 2. Hybrid Search (Vector Cosine Distance OR Keyword Fallback via ILIKE)
        // This ensures exact product names/prices (like "jus alpukat") are never missed by vector distance thresholding.
        String sql = """
            SELECT id, document_id, content,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM document_chunks
            WHERE project_id = ?
              AND (
                  (embedding IS NOT NULL AND (1 - (embedding <=> ?::vector)) >= ?)
                  OR content ILIKE ?
              )
            ORDER BY
              CASE WHEN content ILIKE ? THEN 1.0 ELSE (1 - (embedding <=> ?::vector)) END DESC
            LIMIT ?
        """;

        String keywordPattern = "%" + query.trim().replaceAll("\\s+", "%") + "%";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RetrievedContext(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("content"),
                        Math.max(0.0, rs.getDouble("similarity"))
                ),
                vectorStr, projectId, vectorStr, minSimilarity, keywordPattern,
                keywordPattern, vectorStr, topK
        );
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
