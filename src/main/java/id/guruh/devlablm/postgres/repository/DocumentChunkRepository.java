package id.guruh.devlablm.postgres.repository;

import id.guruh.devlablm.postgres.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByProjectId(Long projectId);

    List<DocumentChunkEntity> findByDocumentId(Long documentId);

    long countByProjectId(Long projectId);

    @Modifying
    @Query("DELETE FROM DocumentChunkEntity c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
