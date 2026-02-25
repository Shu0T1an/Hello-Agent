package cn.ts.agent.tool.shell;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ShellOutputLimiter {

    private ShellOutputLimiter() {
    }

    public static LimitResult limit(String raw, int maxLines, int maxBytes) {
        String normalized = raw == null ? "" : raw;
        boolean truncated = false;

        String byLine = normalized;
        if (maxLines > 0) {
            String[] lines = normalized.split("\\R", -1);
            if (lines.length > maxLines) {
                List<String> selected = new ArrayList<>(maxLines);
                for (int i = 0; i < maxLines; i++) {
                    selected.add(lines[i]);
                }
                byLine = String.join(System.lineSeparator(), selected);
                truncated = true;
            }
        }

        byte[] bytes = byLine.getBytes(StandardCharsets.UTF_8);
        if (maxBytes > 0 && bytes.length > maxBytes) {
            byLine = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
            truncated = true;
        }

        return new LimitResult(byLine, truncated);
    }

    public record LimitResult(String content, boolean truncated) {
    }
}

