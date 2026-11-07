package id.guruh.devlablm.sqlite.service;

import id.guruh.devlablm.security.CryptoService;
import id.guruh.devlablm.sqlite.entity.AiSetting;
import id.guruh.devlablm.sqlite.entity.DatabaseSetting;
import id.guruh.devlablm.sqlite.repository.AiSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AiSettingService {

    private final AiSettingRepository repo;
    private final CryptoService crypto;

    public AiSettingService(AiSettingRepository repo, CryptoService crypto) {
        this.repo = repo;
        this.crypto = crypto;
    }

    @Transactional(value = "sqliteTransactionManager", readOnly = true)
    public Optional<AiSetting> load() {
        return repo.findById(AiSetting.SINGLETON_ID);
    }

    public String decryptPassword(AiSetting s) {
        return crypto.decrypt(s.getAiKey());
    }


    @Transactional("sqliteTransactionManager")
    public AiSetting save(String aiUrl, String aiKey, String modelName) {
        AiSetting s = repo.findById(AiSetting.SINGLETON_ID).orElseGet(AiSetting::new);
        s.setAiUrl(aiUrl);
        s.setAiKey(crypto.encrypt(aiKey));
        s.setModelName(modelName);
        return repo.save(s);
    }
}