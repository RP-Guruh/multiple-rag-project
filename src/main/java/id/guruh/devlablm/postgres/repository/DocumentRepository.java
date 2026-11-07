package id.guruh.devlablm.postgres.repository;

import id.guruh.devlablm.postgres.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<DocumentEntity> findByProjectIdAndSha256Hash(Long projectId, String sha256Hash);

    boolean existsByProjectIdAndSha256Hash(Long projectId, String sha256Hash);

    List<DocumentEntity> findByProjectIdAndStatus(Long projectId, String status);

    long countByProjectId(Long projectId);
}
