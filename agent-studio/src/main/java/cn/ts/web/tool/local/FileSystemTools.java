package cn.ts.web.tool.local;

import cn.ts.web.tool.local.fs.FileOpsService;
import cn.ts.web.tool.local.fs.FileToolException;
import cn.ts.web.tool.local.fs.FileToolProperties;
import cn.ts.web.tool.local.fs.SearchService;
import cn.ts.web.tool.local.fs.ToolErrorCodes;
import cn.ts.web.tool.local.fs.ToolResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class FileSystemTools {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemTools.class);

    private final FileToolProperties properties;
    private final FileOpsService fileOpsService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public FileSystemTools(
            FileToolProperties properties,
            FileOpsService fileOpsService,
            SearchService searchService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.fileOpsService = fileOpsService;
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "Read", description = "Read text file content with optional offset and limit.")
    public String read(@ToolParam(description = "Read request") ReadRequest request) {
        if (!properties.isEnabled()) {
            return toJson(ToolResponse.error(ToolErrorCodes.FILE_TOOL_DISABLED, "File tools are disabled"));
        }
        try {
            var data = fileOpsService.read(
                    request == null ? null : request.file_path(),
                    request == null ? null : request.offset(),
                    request == null ? null : request.limit(),
                    request == null ? null : request.pages()
            );
            return toJson(ToolResponse.ok("Read succeeded", data));
        } catch (FileToolException ex) {
            return toJson(ToolResponse.error(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in Read", ex);
            return toJson(ToolResponse.error(ToolErrorCodes.INTERNAL_ERROR, ex.getMessage()));
        }
    }

    @Tool(name = "Write", description = "Write text content to file, creating or overwriting it.")
    public String write(@ToolParam(description = "Write request") WriteRequest request) {
        if (!properties.isEnabled()) {
            return toJson(ToolResponse.error(ToolErrorCodes.FILE_TOOL_DISABLED, "File tools are disabled"));
        }
        try {
            var data = fileOpsService.write(
                    request == null ? null : request.file_path(),
                    request == null ? null : request.content()
            );
            return toJson(ToolResponse.ok("Write succeeded", data));
        } catch (FileToolException ex) {
            return toJson(ToolResponse.error(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in Write", ex);
            return toJson(ToolResponse.error(ToolErrorCodes.INTERNAL_ERROR, ex.getMessage()));
        }
    }

    @Tool(name = "Edit", description = "Edit file by replacing old_string with new_string.")
    public String edit(@ToolParam(description = "Edit request") EditRequest request) {
        if (!properties.isEnabled()) {
            return toJson(ToolResponse.error(ToolErrorCodes.FILE_TOOL_DISABLED, "File tools are disabled"));
        }
        try {
            var data = fileOpsService.edit(
                    request == null ? null : request.file_path(),
                    request == null ? null : request.old_string(),
                    request == null ? null : request.new_string(),
                    request == null ? null : request.replace_all()
            );
            return toJson(ToolResponse.ok("Edit succeeded", data));
        } catch (FileToolException ex) {
            return toJson(ToolResponse.error(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in Edit", ex);
            return toJson(ToolResponse.error(ToolErrorCodes.INTERNAL_ERROR, ex.getMessage()));
        }
    }

    @Tool(name = "Glob", description = "Find file paths by glob pattern.")
    public String glob(@ToolParam(description = "Glob request") GlobRequest request) {
        if (!properties.isEnabled()) {
            return toJson(ToolResponse.error(ToolErrorCodes.FILE_TOOL_DISABLED, "File tools are disabled"));
        }
        try {
            var data = searchService.glob(
                    request == null ? null : request.pattern(),
                    request == null ? null : request.path()
            );
            return toJson(ToolResponse.ok("Glob succeeded", data));
        } catch (FileToolException ex) {
            return toJson(ToolResponse.error(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in Glob", ex);
            return toJson(ToolResponse.error(ToolErrorCodes.INTERNAL_ERROR, ex.getMessage()));
        }
    }

    @Tool(name = "Grep", description = "Search file content by regex pattern.")
    public String grep(@ToolParam(description = "Grep request") GrepRequest request) {
        if (!properties.isEnabled()) {
            return toJson(ToolResponse.error(ToolErrorCodes.FILE_TOOL_DISABLED, "File tools are disabled"));
        }
        try {
            var data = searchService.grep(
                    request == null ? null : request.pattern(),
                    request == null ? null : request.path(),
                    request == null ? null : request.glob(),
                    request == null ? null : request.output_mode(),
                    request == null ? null : request.type(),
                    request == null ? null : request.ignore_case(),
                    request == null ? null : request.context(),
                    request == null ? null : request.after_context(),
                    request == null ? null : request.before_context(),
                    request == null ? null : request.multiline()
            );
            return toJson(ToolResponse.ok("Grep succeeded", data));
        } catch (FileToolException ex) {
            return toJson(ToolResponse.error(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error in Grep", ex);
            return toJson(ToolResponse.error(ToolErrorCodes.INTERNAL_ERROR, ex.getMessage()));
        }
    }

    private String toJson(ToolResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            return "{\"status\":\"error\",\"errorCode\":\"INTERNAL_ERROR\",\"message\":\"Serialization failed\"}";
        }
    }

    public record ReadRequest(
            String file_path,
            Integer offset,
            Integer limit,
            String pages
    ) {
    }

    public record WriteRequest(
            String file_path,
            String content
    ) {
    }

    public record EditRequest(
            String file_path,
            String old_string,
            String new_string,
            Boolean replace_all
    ) {
    }

    public record GlobRequest(
            String pattern,
            String path
    ) {
    }

    public record GrepRequest(
            String pattern,
            String path,
            String glob,
            String output_mode,
            String type,
            Boolean ignore_case,
            Integer context,
            Integer after_context,
            Integer before_context,
            Boolean multiline
    ) {
    }
}

