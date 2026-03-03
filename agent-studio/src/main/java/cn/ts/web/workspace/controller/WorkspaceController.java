package cn.ts.web.workspace.controller;

import cn.ts.web.shared.response.Result;
import cn.ts.web.workspace.service.WorkspaceArchiveService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceArchiveService workspaceArchiveService;

    public WorkspaceController(WorkspaceArchiveService workspaceArchiveService) {
        this.workspaceArchiveService = workspaceArchiveService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWorkspace() {
        byte[] bytes = workspaceArchiveService.exportWorkspace();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workspace-export.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(bytes);
    }

    @PostMapping("/import")
    public Result<Map<String, Integer>> importWorkspace(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "merge") String strategy) {
        int imported = workspaceArchiveService.importWorkspace(file, strategy);
        return Result.success(Map.of("importedFiles", imported));
    }
}
