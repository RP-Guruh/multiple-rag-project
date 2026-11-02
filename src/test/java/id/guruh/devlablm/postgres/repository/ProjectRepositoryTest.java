package id.guruh.devlablm.postgres.repository;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectRepositoryTest {

    @Autowired(required = false)
    private ProjectRepository projectRepository;

    @Test
    void testProjectRepository() {
        assertThat(projectRepository).isNotNull();

        String projectName = "Test Project " + System.currentTimeMillis();
        ProjectEntity entity = new ProjectEntity();
        entity.setName(projectName);
        entity.setDescription("Integration test project");
        entity.setSystemPrompt("You are a test assistant");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        ProjectEntity saved = projectRepository.save(entity);
        assertThat(saved.getId()).isNotNull();

        Optional<ProjectEntity> found = projectRepository.findByName(projectName);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(projectName);

        projectRepository.delete(saved);
        assertThat(projectRepository.findByName(projectName)).isEmpty();
    }
}
