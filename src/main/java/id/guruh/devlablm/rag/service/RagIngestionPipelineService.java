package id.guruh.devlablm.rag.service;

import id.guruh.devlablm.postgres.entity.DocumentEntity;
import id.guruh.devlablm.postgres.repository.DocumentChunkRepository;
import id.guruh.devlablm.postgres.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class RagIngestionPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionPipelineService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentParserService documentParserService;
    private final TextChunkerService textChunkerService;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public RagIngestionPipelineService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentParserService documentParserService,
            TextChunkerService textChunkerService,
            EmbeddingService embeddingService,
            @Qualifier("postgresDataSource") DataSource dataSource) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentParserService = documentParserService;
        this.textChunkerService = textChunkerService;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Executes the complete RAG training pipeline for all pending/uploaded documents of a project.
     * Reports progress (0.0 to 1.0) and activity log messages to callbacks for UI updates.
     */
    public void trainProjectKnowledge(Long projectId, Consumer<Double> onProgress, Consumer<String> onLog) throws Exception {
        List<DocumentEntity> toProcess = documentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        if (toProcess.isEmpty()) {
            onLog.accept("[INFO] No documents found in this project to train.");
            onProgress.accept(1.0);
            return;
        }

        onLog.accept(String.format("[PIPELINE START] Processing %d document(s) for project ID %d...", toProcess.size(), projectId));

        double totalSteps = toProcess.size() * 3.0; // Extract, Chunk, Embed
        double currentStep = 0.0;

        for (DocumentEntity doc : toProcess) {
            try {
                // Step 1: Text Extraction
                onLog.accept(String.format("[STEP 1/3] Extracting text from '%s' (%s)...", doc.getFileName(), doc.getFileType()));
                doc.setStatus("EXTRACTING");
                documentRepository.save(doc);

                File file = new File(doc.getFilePath());
                if (!file.exists()) {
                    throw new IllegalStateException("File not found on disk: " + doc.getFilePath());
                }

                String text = documentParserService.extractText(file, doc.getFileType());
                if (text == null || text.isBlank()) {
                    throw new IllegalStateException("Extracted text is empty or unreadable.");
                }

                currentStep += 1.0;
                onProgress.accept(currentStep / totalSteps);
                onLog.accept(String.format(" -> Extracted %d characters.", text.length()));

                // Step 2: Chunking
                onLog.accept(String.format("[STEP 2/3] Chunking '%s' into semantic segments...", doc.getFileName()));
                doc.setStatus("CHUNKING");
                documentRepository.save(doc);

                List<TextChunkerService.Chunk> chunks = textChunkerService.splitIntoChunks(text);
                onLog.accept(String.format(" -> Generated %d chunks.", chunks.size()));

                currentStep += 1.0;
                onProgress.accept(currentStep / totalSteps);

                // Step 3: Embeddings & Vector Indexing
                onLog.accept(String.format("[STEP 3/3] Generating vector embeddings for %d chunks...", chunks.size()));
                doc.setStatus("EMBEDDING");
                documentRepository.save(doc);

                List<String> chunkTexts = chunks.stream().map(TextChunkerService.Chunk::text).toList();
                List<float[]> embeddings = embeddingService.computeEmbeddings(chunkTexts);

                onLog.accept(" -> Persisting chunks and pgvector embeddings to PostgreSQL...");
                saveChunksWithEmbeddings(doc, chunks, embeddings);

                // Complete
                doc.setStatus("INDEXED");
                doc.setChunkCount(chunks.size());
                doc.setErrorMessage(null);
                documentRepository.save(doc);

                currentStep += 1.0;
                onProgress.accept(currentStep / totalSteps);
                onLog.accept(String.format("[COMPLETED] '%s' indexed successfully (%d chunks).", doc.getFileName(), chunks.size()));

            } catch (Exception ex) {
                log.error("Failed processing document {}: {}", doc.getFileName(), ex.getMessage(), ex);
                doc.setStatus("FAILED");
                doc.setErrorMessage(ex.getMessage());
                documentRepository.save(doc);
                onLog.accept(String.format("[ERROR] Failed '%s': %s", doc.getFileName(), ex.getMessage()));
            }
        }

        onProgress.accept(1.0);
        onLog.accept("\n==================== PIPELINE FINISHED ====================");
    }

    @Transactional
    protected void saveChunksWithEmbeddings(DocumentEntity doc, List<TextChunkerService.Chunk> chunks, List<float[]> embeddings) {
        // Remove previous chunks for this document if re-training
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", doc.getId());

        String insertSql = """
            INSERT INTO document_chunks (document_id, project_id, chunk_index, content, token_count, embedding, created_at)
            VALUES (?, ?, ?, ?, ?, ?::vector, now())
        """;

        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextChunkerService.Chunk chunk = chunks.get(i);
            String vectorString = (embeddings != null && i < embeddings.size())
                    ? toVectorString(embeddings.get(i))
                    : null;

            batchArgs.add(new Object[]{
                    doc.getId(),
                    doc.getProject().getId(),
                    chunk.index(),
                    chunk.text(),
                    chunk.tokenCount(),
                    vectorString
            });
        }

        jdbcTemplate.batchUpdate(insertSql, batchArgs);
    }

    private String toVectorString(float[] vector) {
        if (vector == null || vector.length == 0) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
