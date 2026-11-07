package id.guruh.devlablm.postgres.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.guruh.devlablm.config.DynamicPostgresDataSource;
import id.guruh.devlablm.config.PostgresSchemaInitializer;
import id.guruh.devlablm.sqlite.entity.DatabaseSetting;
import id.guruh.devlablm.sqlite.service.DatabaseSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PostgresConnectionService {

    private static final Logger log = LoggerFactory.getLogger(PostgresConnectionService.class);

    private final DynamicPostgresDataSource dynamicPostgresDataSource;
    private final PostgresSchemaInitializer schemaInitializer;
    private final DatabaseSettingService databaseSettingService;

    public PostgresConnectionService(
            DynamicPostgresDataSource dynamicPostgresDataSource,
            PostgresSchemaInitializer schemaInitializer,
            DatabaseSettingService databaseSettingService) {
        this.dynamicPostgresDataSource = dynamicPostgresDataSource;
        this.schemaInitializer = schemaInitializer;
        this.databaseSettingService = databaseSettingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initConnection();
    }

    public synchronized void initConnection() {
        if (isConnected()) {
            return;
        }

        Optional<DatabaseSetting> opt = databaseSettingService.load();
        if (opt.isPresent()) {
            DatabaseSetting setting = opt.get();
            if (isValid(setting)) {
                log.info("Ditemukan pengaturan PostgreSQL di SQLite. Memulai inisialisasi koneksi...");
                try {
                    configureAndMigrate(setting, -1);
                    log.info("Koneksi PostgreSQL dan migrasi Flyway berhasil diinisialisasi saat startup.");
                } catch (Exception e) {
                    log.warn("Tidak dapat terhubung ke PostgreSQL saat startup: {}. Aplikasi tetap berjalan.", e.getMessage());
                }
            }
        } else {
            log.info("Pengaturan PostgreSQL belum tersedia di SQLite. Menunggu konfigurasi dari pengguna.");
        }
    }

    public synchronized void configureAndMigrate(DatabaseSetting setting) throws Exception {
        configureAndMigrate(setting, 5000);
    }

    public synchronized void configureAndMigrate(DatabaseSetting setting, long initFailTimeout) throws Exception {
        if (!isValid(setting)) {
            throw new IllegalArgumentException("Konfigurasi database tidak lengkap. Host, nama database, dan username wajib diisi.");
        }

        String jdbcUrl = buildJdbcUrl(setting);
        String decryptedPassword = databaseSettingService.decryptPassword(setting);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(setting.getUsername());
        config.setPassword(decryptedPassword);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("PostgresHikariPool");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        config.setInitializationFailTimeout(initFailTimeout);

        HikariDataSource newDataSource = new HikariDataSource(config);

        try {
            // Jalankan migrasi Flyway
            schemaInitializer.migrate(newDataSource, setting.getSchema());
            // Update delegate pada DynamicPostgresDataSource
            dynamicPostgresDataSource.setDelegate(newDataSource);
        } catch (Exception e) {
            newDataSource.close();
            throw e;
        }
    }

    public boolean isConnected() {
        return dynamicPostgresDataSource.isConfigured();
    }

    public String buildJdbcUrl(DatabaseSetting setting) {
        String host = (setting.getHost() != null && !setting.getHost().isBlank()) ? setting.getHost().trim() : "localhost";
        int port = (setting.getPort() != null) ? setting.getPort() : 5432;
        String db = (setting.getDatabase() != null) ? setting.getDatabase().trim() : "postgres";
        String schema = (setting.getSchema() != null && !setting.getSchema().isBlank()) ? setting.getSchema().trim() : "public";
        boolean ssl = Boolean.TRUE.equals(setting.getSslEnabled());

        StringBuilder sb = new StringBuilder("jdbc:postgresql://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(db)
                .append("?currentSchema=")
                .append(schema);

        if (ssl) {
            sb.append("&sslmode=require");
        }
        return sb.toString();
    }

    private boolean isValid(DatabaseSetting setting) {
        return setting.getHost() != null && !setting.getHost().isBlank()
                && setting.getDatabase() != null && !setting.getDatabase().isBlank()
                && setting.getUsername() != null && !setting.getUsername().isBlank();
    }
}
