-- Alter column embedding to 768 dimensions (Ollama nomic-embed-text)
-- Note: In pgvector, HNSW indexing requires an explicit dimension definition.
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(768);

-- Recreate HNSW index for 768 dimensions
DROP INDEX IF EXISTS idx_document_chunks_embedding_hnsw;

CREATE INDEX idx_document_chunks_embedding_hnsw 
ON document_chunks 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
