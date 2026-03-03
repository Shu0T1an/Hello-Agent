package cn.ts.web.skills.model;

import java.util.List;
import java.util.Map;

public class SkillDetail {

    private String id;
    private String name;
    private String skillFile;
    private Map<String, Object> frontMatter;
    private List<SkillSection> sections;
    private List<SkillReference> references;

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

    public String getSkillFile() {
        return skillFile;
    }

    public void setSkillFile(String skillFile) {
        this.skillFile = skillFile;
    }

    public Map<String, Object> getFrontMatter() {
        return frontMatter;
    }

    public void setFrontMatter(Map<String, Object> frontMatter) {
        this.frontMatter = frontMatter;
    }

    public List<SkillSection> getSections() {
        return sections;
    }

    public void setSections(List<SkillSection> sections) {
        this.sections = sections;
    }

    public List<SkillReference> getReferences() {
        return references;
    }

    public void setReferences(List<SkillReference> references) {
        this.references = references;
    }
}

