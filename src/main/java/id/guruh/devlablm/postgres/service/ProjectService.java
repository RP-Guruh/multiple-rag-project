package id.guruh.devlablm.postgres.service;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // View all
    @Transactional(readOnly = true)
    public List<ProjectEntity> findAll() {
        return projectRepository.findAllByOrderByUpdatedAtDesc();
    }

    // View per id
    @Transactional(readOnly = true)
    public Optional<ProjectEntity> getById(Long id) {
        return projectRepository.findById(id);
    }

    // Add
    @Transactional
    public ProjectEntity create(String name, String description, String systemPrompt) {
        String cleanName = requireName(name);
        if (projectRepository.existsByNameIgnoreCase(cleanName)) {
            throw new IllegalArgumentException("Nama project sudah dipakai: " + cleanName);
        }

        ProjectEntity p = new ProjectEntity();
        p.setName(cleanName);
        p.setDescription(description);
        p.setSystemPrompt(systemPrompt);
        return projectRepository.save(p);
    }

    // Update
    @Transactional
    public ProjectEntity update(Long id, String name, String description, String systemPrompt) {
        ProjectEntity p = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project tidak ditemukan: " + id));

        String cleanName = requireName(name);
        if (!p.getName().equalsIgnoreCase(cleanName)
                && projectRepository.existsByNameIgnoreCase(cleanName)) {
            throw new IllegalArgumentException("Nama project sudah dipakai: " + cleanName);
        }

        p.setName(cleanName);
        p.setDescription(description);
        p.setSystemPrompt(systemPrompt);
        return projectRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        ProjectEntity p = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project tidak ditemukan: " + id));

        // Hapus folder dokumen lokal project: data/projects/{id}
        Path projectDir = Path.of("data", "projects", String.valueOf(id));
        if (Files.exists(projectDir)) {
            try (var stream = Files.walk(projectDir)) {
                stream.sorted((a, b) -> b.compareTo(a)) // Hapus file/anak folder dulu baru folder induk
                      .forEach(path -> {
                          try {
                              Files.deleteIfExists(path);
                          } catch (IOException ignored) {}
                      });
            } catch (IOException ignored) {}
        }

        projectRepository.delete(p);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nama project wajib diisi");
        }
        return name.trim();
    }
}