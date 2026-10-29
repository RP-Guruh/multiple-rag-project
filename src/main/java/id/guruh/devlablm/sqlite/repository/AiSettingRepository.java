package id.guruh.devlablm.sqlite.repository;

import id.guruh.devlablm.sqlite.entity.AiSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {
}
