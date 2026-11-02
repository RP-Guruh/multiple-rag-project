package id.guruh.devlablm.postgres.repository;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    Optional<ProjectEntity> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
    List<ProjectEntity> findAllByOrderByUpdatedAtDesc();
}