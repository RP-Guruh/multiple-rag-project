package id.guruh.devlablm.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkerService {

    private static final int DEFAULT_CHUNK_SIZE = 800; // characters
    private static final int DEFAULT_CHUNK_OVERLAP = 120; // characters

    public record Chunk(int index, String text, int tokenCount) {}

    public List<Chunk> splitIntoChunks(String fullText) {
        return splitIntoChunks(fullText, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public List<Chunk> splitIntoChunks(String fullText, int chunkSize, int chunkOverlap) {
        List<Chunk> chunks = new ArrayList<>();
        if (fullText == null || fullText.isBlank()) {
            return chunks;
        }

        String normalized = fullText.replaceAll("\r\n", "\n").replaceAll("\r", "\n").strip();
        if (normalized.length() <= chunkSize) {
            chunks.add(new Chunk(0, normalized, estimateTokens(normalized)));
            return chunks;
        }

        int start = 0;
        int chunkIndex = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());

            // If we are not at the end of the text, look for natural split points
            if (end < normalized.length()) {
                int naturalBreak = findNaturalBreakPoint(normalized, start, end);
                if (naturalBreak > start + (chunkSize / 2)) {
                    end = naturalBreak;
                }
            }

            String chunkContent = normalized.substring(start, end).strip();
            if (!chunkContent.isBlank()) {
                chunks.add(new Chunk(chunkIndex++, chunkContent, estimateTokens(chunkContent)));
            }

            if (end >= normalized.length()) {
                break;
            }

            // Move start forward, accounting for overlap
            start = Math.max(start + 1, end - chunkOverlap);
        }

        return chunks;
    }

    private int findNaturalBreakPoint(String text, int start, int end) {
        // Priority 1: Paragraph split
        int idx = text.lastIndexOf("\n\n", end);
        if (idx >= start + (end - start) / 2) {
            return idx + 2;
        }

        // Priority 2: Sentence split (. or ? or !)
        for (int i = end - 1; i >= start + (end - start) / 2; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }

        // Priority 3: Single newline
        idx = text.lastIndexOf('\n', end);
        if (idx >= start + (end - start) / 2) {
            return idx + 1;
        }

        // Priority 4: Whitespace
        idx = text.lastIndexOf(' ', end);
        if (idx >= start + (end - start) / 2) {
            return idx + 1;
        }

        return end;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Conservative heuristic: ~4 characters per token in western/indonesian text
        return Math.max(1, text.length() / 4);
    }
}
