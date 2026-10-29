package id.guruh.devlablm.sqlite.service;

import id.guruh.devlablm.sqlite.entity.AiSetting;
import id.guruh.devlablm.sqlite.repository.AiSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AiSettingService {

    private final AiSettingRepository repo;

    public AiSettingService(AiSettingRepository repo) {
        this.repo = repo;
    }

    @Transactional(value = "sqliteTransactionManager", readOnly = true)
    public Optional<AiSetting> load() {
        return repo.findById(AiSetting.SINGLETON_ID);
    }

    @Transactional("sqliteTransactionManager")
    public AiSetting save(String aiUrl, String aiKey, String modelName) {
        AiSetting s = repo.findById(AiSetting.SINGLETON_ID).orElseGet(AiSetting::new);
        s.setAiUrl(aiUrl);
        s.setAiKey(aiKey);
        s.setModelName(modelName);
        return repo.save(s);
    }
}