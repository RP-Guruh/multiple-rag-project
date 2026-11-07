package id.guruh.devlablm.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class PostgresSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchemaInitializer.class);

    /** Dipanggil setelah koneksi Postgres berhasil. */
    public void migrate(DataSource ds) {
        migrate(ds, "public");
    }

    public void migrate(DataSource ds, String schema) {
        String targetSchema = (schema == null || schema.isBlank()) ? "public" : schema.trim();
        log.info("Menjalankan migrasi Flyway skema PostgreSQL: {}", targetSchema);

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .schemas(targetSchema)
                .defaultSchema(targetSchema)
                .locations("classpath:db/migration/postgres")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(false)
                .load();

        try {
            flyway.repair();
        } catch (Exception ex) {
            log.warn("Flyway repair notice: {}", ex.getMessage());
        }

        flyway.migrate();
        log.info("Migrasi Flyway untuk skema {} berhasil selesai.", targetSchema);
    }
}
