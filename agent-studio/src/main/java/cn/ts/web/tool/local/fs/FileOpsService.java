package cn.ts.web.tool.local.fs;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileOpsService {

    private final PathPolicyService pathPolicyService;
    private final FileToolProperties properties;

    public FileOpsService(PathPolicyService pathPolicyService, FileToolProperties properties) {
        this.pathPolicyService = pathPolicyService;
        this.properties = properties;
    }

    public Map<String, Object> read(String filePath, Integer offset, Integer limit, String pages) {
        if (pages != null && !pages.isBlank()) {
            throw new FileToolException(ToolErrorCodes.UNSUPPORTED_PAGES_FOR_TEXT, "pages is only supported for PDF in future versions");
        }
        Path path = pathPolicyService.resolveReadPath(filePath);
        assertRegularFile(path);
        String content = readUtf8Text(path);

        List<String> lines = content.lines().toList();
        int totalLines = lines.size();
        int start = Math.max(0, (offset == null ? 1 : offset) - 1);
        int limitLines = limit == null ? properties.getMaxReadLines() : Math.min(limit, properties.getMaxReadLines());

        if (start >= totalLines) {
            start = totalLines;
        }
        int end = Math.min(totalLines, start + Math.max(0, limitLines));
        String sliced = String.join(System.lineSeparator(), lines.subList(start, end));

        Map<String, Object> data = new HashMap<>();
        data.put("path", path.toString());
        data.put("offset", start + 1);
        data.put("limit", limitLines);
        data.put("totalLines", totalLines);
        data.put("returnedLines", end - start);
        data.put("content", sliced);
        return data;
    }

    public Map<String, Object> write(String filePath, String content) {
        if (content == null) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "content is required");
        }
        Path path = pathPolicyService.resolveWritePath(filePath);
        try {
            Path parent = path.getParent();
            if (parent == null) {
                throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "Path parent is required: " + path);
            }
            Files.createDirectories(parent);
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Map<String, Object> data = new HashMap<>();
            data.put("path", path.toString());
            data.put("bytes", Files.size(path));
            return data;
        } catch (FileToolException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to write file: " + path, ex);
        }
    }

    public Map<String, Object> edit(String filePath, String oldString, String newString, Boolean replaceAll) {
        if (oldString == null || oldString.isEmpty()) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "old_string is required");
        }
        if (newString == null) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "new_string is required");
        }

        Path path = pathPolicyService.resolveWritePath(filePath);
        assertRegularFile(path);
        String content = readUtf8Text(path);

        boolean replace = Boolean.TRUE.equals(replaceAll);
        if (replace && oldString.equals(newString)) {
            throw new FileToolException(ToolErrorCodes.NO_CHANGES_MADE, "old_string and new_string are identical");
        }

        EditResult result = replace ? replaceAll(content, oldString, newString) : replaceSingle(content, oldString, newString);
        if (result.updatedContent().equals(content)) {
            throw new FileToolException(ToolErrorCodes.NO_CHANGES_MADE, "No changes made after edit");
        }

        try {
            Files.writeString(path, result.updatedContent(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to write edited content: " + path, ex);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("path", path.toString());
        data.put("replacedCount", result.replacedCount());
        data.put("bytes", sizeOrZero(path));
        return data;
    }

    private void assertRegularFile(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileToolException(ToolErrorCodes.FILE_NOT_FOUND, "File not found: " + path);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileToolException(ToolErrorCodes.NOT_A_FILE, "Path is not a regular file: " + path);
        }
    }

    String readUtf8Text(Path path) {
        try {
            long size = Files.size(path);
            if (size > properties.getMaxReadBytes()) {
                throw new FileToolException(ToolErrorCodes.FILE_TOO_LARGE, "File exceeds max read bytes: " + size);
            }
            byte[] bytes = Files.readAllBytes(path);
            if (looksBinary(bytes)) {
                throw new FileToolException(ToolErrorCodes.BINARY_FILE_NOT_SUPPORTED, "Binary file is not supported: " + path);
            }

            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (FileToolException ex) {
            throw ex;
        } catch (CharacterCodingException ex) {
            throw new FileToolException(ToolErrorCodes.BINARY_FILE_NOT_SUPPORTED, "File is not valid UTF-8 text: " + path, ex);
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to read file: " + path, ex);
        }
    }

    private boolean looksBinary(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private EditResult replaceSingle(String content, String oldString, String newString) {
        int first = content.indexOf(oldString);
        if (first < 0) {
            throw new FileToolException(ToolErrorCodes.OLD_STRING_NOT_FOUND, "old_string not found");
        }
        int second = content.indexOf(oldString, first + oldString.length());
        if (second >= 0) {
            throw new FileToolException(ToolErrorCodes.OLD_STRING_NOT_UNIQUE, "old_string appears multiple times, set replace_all=true");
        }

        String updated = content.substring(0, first) + newString + content.substring(first + oldString.length());
        return new EditResult(updated, 1);
    }

    private EditResult replaceAll(String content, String oldString, String newString) {
        int count = 0;
        int index = 0;
        while (true) {
            int found = content.indexOf(oldString, index);
            if (found < 0) {
                break;
            }
            count++;
            index = found + oldString.length();
        }
        if (count == 0) {
            throw new FileToolException(ToolErrorCodes.OLD_STRING_NOT_FOUND, "old_string not found");
        }
        return new EditResult(content.replace(oldString, newString), count);
    }

    private long sizeOrZero(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private record EditResult(String updatedContent, int replacedCount) {
    }
}

