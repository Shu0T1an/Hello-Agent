package cn.ts.web.skills.dto;

import lombok.Data;

@Data
public class SkillLifecycleRequest {

    private String name;
    private String content;
    private Boolean enable;
}
