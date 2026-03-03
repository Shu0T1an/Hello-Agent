package cn.ts.web.skills.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent.skills")
public class SkillsProperties {

    private boolean enabled = true;
    private List<String> roots = new ArrayList<>(List.of("skills"));
    private int maxListItems = 500;
    private int maxDetailChars = 20000;
    private int maxReferenceBytes = 1024 * 1024;
    private boolean promptInjectionEnabled = true;
    private boolean toolEnabled = true;
    private int promptMaxSkills = 20;
    private String customizedRoot;
    private String activeRoot;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRoots() {
        return roots;
    }

    public void setRoots(List<String> roots) {
        this.roots = roots;
    }

    public int getMaxListItems() {
        return maxListItems;
    }

    public void setMaxListItems(int maxListItems) {
        this.maxListItems = maxListItems;
    }

    public int getMaxDetailChars() {
        return maxDetailChars;
    }

    public void setMaxDetailChars(int maxDetailChars) {
        this.maxDetailChars = maxDetailChars;
    }

    public int getMaxReferenceBytes() {
        return maxReferenceBytes;
    }

    public void setMaxReferenceBytes(int maxReferenceBytes) {
        this.maxReferenceBytes = maxReferenceBytes;
    }

    public boolean isPromptInjectionEnabled() {
        return promptInjectionEnabled;
    }

    public void setPromptInjectionEnabled(boolean promptInjectionEnabled) {
        this.promptInjectionEnabled = promptInjectionEnabled;
    }

    public boolean isToolEnabled() {
        return toolEnabled;
    }

    public void setToolEnabled(boolean toolEnabled) {
        this.toolEnabled = toolEnabled;
    }

    public int getPromptMaxSkills() {
        return promptMaxSkills;
    }

    public void setPromptMaxSkills(int promptMaxSkills) {
        this.promptMaxSkills = promptMaxSkills;
    }

    public String getCustomizedRoot() {
        return customizedRoot;
    }

    public void setCustomizedRoot(String customizedRoot) {
        this.customizedRoot = customizedRoot;
    }

    public String getActiveRoot() {
        return activeRoot;
    }

    public void setActiveRoot(String activeRoot) {
        this.activeRoot = activeRoot;
    }
}
