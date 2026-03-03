package cn.ts.web.skills.dto;

import lombok.Data;

@Data
public class SkillImportRequest {

    private String url;
    private Boolean overwrite;
    private Boolean enableAfterImport;
}
