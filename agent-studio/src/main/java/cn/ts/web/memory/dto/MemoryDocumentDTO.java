package cn.ts.web.memory.dto;

public record MemoryDocumentDTO(
        String filePath,
        boolean exists,
        String content,
        int length,
        int maxChars
) {
}
