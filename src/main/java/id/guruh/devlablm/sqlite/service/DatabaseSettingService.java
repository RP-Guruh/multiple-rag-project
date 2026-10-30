package id.guruh.devlablm.sqlite.service;

import id.guruh.devlablm.security.CryptoService;
import id.guruh.devlablm.sqlite.entity.DatabaseSetting;
import id.guruh.devlablm.sqlite.repository.DatabaseSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DatabaseSettingService {

    private final DatabaseSettingRepository repo;
    private final CryptoService crypto;

    public DatabaseSettingService(DatabaseSettingRepository repo, CryptoService crypto) {
        this.repo = repo;
        this.crypto = crypto;
    }

    @Transactional(value = "sqliteTransactionManager", readOnly = true)
    public Optional<DatabaseSetting> load() {
        return repo.findById(DatabaseSetting.SINGLETON_ID);
    }

    public String decryptPassword(DatabaseSetting s) {
        return crypto.decrypt(s.getPassword());
    }

    @Transactional("sqliteTransactionManager")
    public DatabaseSetting save(String host, Integer port, String database,
                                String username, String password,
                                String schema, Boolean sslEnabled) {
        DatabaseSetting s = repo.findById(DatabaseSetting.SINGLETON_ID)
                .orElseGet(DatabaseSetting::new);

        s.setHost(host);
        s.setPort(port);
        s.setDatabase(database);
        s.setUsername(username);
        s.setPassword(crypto.encrypt(password));
        s.setSchema(schema == null || schema.isBlank() ? "public" : schema);
        s.setSslEnabled(sslEnabled != null && sslEnabled);

        return repo.save(s);
    }
}