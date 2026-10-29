package id.guruh.devlablm.sqlite.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_setting")
public class AiSetting {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String aiUrl;
    private String aiKey;
    private String modelName;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public String getAiUrl() { return aiUrl; }
    public void setAiUrl(String aiUrl) { this.aiUrl = aiUrl; }

    public String getAiKey() { return aiKey; }
    public void setAiKey(String aiKey) { this.aiKey = aiKey; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
}
