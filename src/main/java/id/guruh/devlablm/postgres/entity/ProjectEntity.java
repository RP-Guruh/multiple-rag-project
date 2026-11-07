package id.guruh.devlablm.postgres.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column( columnDefinition = "text")
    private String system_prompt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }

    public void setSystem_prompt(String system_prompt) { this.system_prompt = system_prompt; }
    public String getSystem_prompt() { return system_prompt; }
    public void setSystemPrompt(String systemPrompt) { this.system_prompt = systemPrompt; }
    public String getSystemPrompt() { return system_prompt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
