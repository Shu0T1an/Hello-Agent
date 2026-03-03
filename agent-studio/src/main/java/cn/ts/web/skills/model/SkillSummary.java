package cn.ts.web.skills.model;

import java.time.Instant;

public class SkillSummary {

    private String id;
    private String name;
    private String description;
    private String triggerSummary;
    private String skillFile;
    private Instant lastModified;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTriggerSummary() {
        return triggerSummary;
    }

    public void setTriggerSummary(String triggerSummary) {
        this.triggerSummary = triggerSummary;
    }

    public String getSkillFile() {
        return skillFile;
    }

    public void setSkillFile(String skillFile) {
        this.skillFile = skillFile;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }
}

