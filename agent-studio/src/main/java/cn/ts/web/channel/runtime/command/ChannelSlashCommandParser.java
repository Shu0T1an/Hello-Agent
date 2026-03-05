package cn.ts.web.channel.runtime.command;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ChannelSlashCommandParser {

    public ParsedSlashCommand parse(String text) {
        if (text == null) {
            return ParsedSlashCommand.notCommand();
        }
        String normalized = text.trim();
        if (normalized.isEmpty() || normalized.charAt(0) != '/') {
            return ParsedSlashCommand.notCommand();
        }

        String commandBody = normalized.substring(1).trim();
        if (commandBody.isEmpty()) {
            return ParsedSlashCommand.error("命令不能为空");
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        boolean tokenStarted = false;

        for (int i = 0; i < commandBody.length(); i++) {
            char ch = commandBody.charAt(i);
            if (inQuote) {
                if (ch == quoteChar) {
                    inQuote = false;
                    tokenStarted = true;
                    continue;
                }
                if (ch == '\\' && i + 1 < commandBody.length()) {
                    char next = commandBody.charAt(i + 1);
                    if (next == quoteChar || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                current.append(ch);
                continue;
            }

            if (Character.isWhitespace(ch)) {
                if (tokenStarted || current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
                continue;
            }

            if (ch == '"' || ch == '\'') {
                inQuote = true;
                quoteChar = ch;
                tokenStarted = true;
                continue;
            }

            current.append(ch);
            tokenStarted = true;
        }

        if (inQuote) {
            return ParsedSlashCommand.error("命令引号未闭合");
        }

        if (tokenStarted || current.length() > 0) {
            tokens.add(current.toString());
        }

        if (tokens.isEmpty() || tokens.get(0).isBlank()) {
            return ParsedSlashCommand.error("命令名不能为空");
        }

        String name = tokens.get(0).toLowerCase(Locale.ROOT);
        List<String> args = tokens.size() > 1 ? tokens.subList(1, tokens.size()) : List.of();
        return ParsedSlashCommand.success(name, args);
    }
}
