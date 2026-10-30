package id.guruh.devlablm.sqlite.repository;

import id.guruh.devlablm.sqlite.entity.DatabaseSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatabaseSettingRepository extends JpaRepository<DatabaseSetting, Long> {
}
