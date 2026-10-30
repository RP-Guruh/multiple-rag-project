package id.guruh.devlablm.sqlite.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "database_setting")
public class DatabaseSetting {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String host;
    private Integer port;       // default 5432
    private String database;    // nama database
    private String username;
    private String password;    // sebaiknya dienkripsi
    private String schema;      // opsional, default public
    private Boolean sslEnabled; // opsional

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setHost(String host) { this.host = host; }
    public String getHost() { return host; }

    public void setPort(Integer port) { this.port = port; }
    public Integer getPort() { return port; }

    public void setDatabase(String database) { this.database = database; }
    public String getDatabase() { return database; }

    public void setUsername(String username) { this.username = username; }
    public String getUsername() { return username; }

    public void setPassword(String password) { this.password = password; }
    public String getPassword() { return password; }

    public void setSchema(String schema) { this.schema = schema; }
    public String getSchema() { return schema; }

    public void setSslEnabled(Boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public Boolean getSslEnabled() { return sslEnabled; }
}
