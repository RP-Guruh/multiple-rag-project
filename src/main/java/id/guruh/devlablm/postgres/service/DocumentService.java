package id.guruh.devlablm.postgres.service;

import id.guruh.devlablm.postgres.entity.DocumentEntity;
import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.repository.DocumentRepository;
import id.guruh.devlablm.postgres.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Path BASE_UPLOAD_DIR = Path.of("data", "projects");

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;

    public DocumentService(DocumentRepository documentRepository, ProjectRepository projectRepository) {
        this.documentRepository = documentRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<DocumentEntity> findByProjectId(Long projectId) {
        return documentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentEntity> findById(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Menyimpan satu file ke storage lokal dan mencatatnya ke database.
     */
    @Transactional
    public DocumentEntity uploadDocument(Long projectId, File sourceFile) throws IOException {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project tidak ditemukan dengan ID: " + projectId));

        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            throw new IllegalArgumentException("File sumber tidak valid atau tidak ditemukan.");
        }

        String originalName = sourceFile.getName();
        String fileType = extractExtension(originalName);
        long fileSize = sourceFile.length();
        String hash = calculateSha256(sourceFile);

        // Cek duplikasi file dalam project yang sama berdasarkan hash
        if (documentRepository.existsByProjectIdAndSha256Hash(projectId, hash)) {
            throw new IllegalArgumentException("File '" + originalName + "' sudah pernah diunggah ke project ini (konten identik).");
        }

        // Tentukan folder penyimpanan: data/projects/{projectId}/docs
        Path projectDocsDir = BASE_UPLOAD_DIR.resolve(String.valueOf(projectId)).resolve("docs");
        if (!Files.exists(projectDocsDir)) {
            Files.createDirectories(projectDocsDir);
        }

        // Buat nama file unik untuk menghindari konflik nama di disk
        String sanitizedName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String uniqueFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + "_" + sanitizedName;
        Path targetPath = projectDocsDir.resolve(uniqueFileName);

        // Salin file fisik
        Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Buat dan simpan Entity
        DocumentEntity entity = new DocumentEntity();
        entity.setProject(project);
        entity.setFileName(originalName);
        entity.setFilePath(targetPath.toString());
        entity.setFileType(fileType.toLowerCase());
        entity.setFileSize(fileSize);
        entity.setSha256Hash(hash);
        entity.setStatus("UPLOADED");
        entity.setChunkCount(0);

        DocumentEntity saved = documentRepository.save(entity);
        log.info("Berhasil mengunggah dokumen [ID: {}] '{}' untuk project ID: {}", saved.getId(), originalName, projectId);
        return saved;
    }

    /**
     * Batch upload beberapa file sekaligus.
     */
    @Transactional
    public List<DocumentEntity> uploadDocuments(Long projectId, List<File> files) {
        List<DocumentEntity> results = new ArrayList<>();
        for (File f : files) {
            try {
                results.add(uploadDocument(projectId, f));
            } catch (Exception e) {
                log.error("Gagal mengunggah file '{}': {}", f.getName(), e.getMessage());
                throw new RuntimeException("Gagal mengunggah " + f.getName() + ": " + e.getMessage(), e);
            }
        }
        return results;
    }

    /**
     * Hapus dokumen dari database beserta file fisiknya di disk.
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            try {
                Path filePath = Path.of(doc.getFilePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Tidak dapat menghapus file fisik di disk: {}", doc.getFilePath(), e);
            }
            documentRepository.delete(doc);
            log.info("Dokumen ID: {} berhasil dihapus.", documentId);
        });
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "unknown";
    }

    private String calculateSha256(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Gagal menghitung hash SHA-256 dari file: " + file.getName(), e);
        }
    }
}
