package cn.ts.web.memory.controller;

import cn.ts.web.memory.dto.MemoryDocumentDTO;
import cn.ts.web.memory.service.MemoryFileService;
import cn.ts.web.shared.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryFileService memoryFileService;

    public MemoryController(MemoryFileService memoryFileService) {
        this.memoryFileService = memoryFileService;
    }

    @GetMapping
    public Result<MemoryDocumentDTO> getMemoryDocument() {
        return Result.success(memoryFileService.readDocument());
    }

    @PutMapping
    public Result<MemoryDocumentDTO> updateMemoryDocument(@RequestBody UpdateMemoryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return Result.success(memoryFileService.writeDocument(request.content()));
    }

    public record UpdateMemoryRequest(String content) {
    }
}
