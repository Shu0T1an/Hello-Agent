package cn.ts.web.channel.runtime.command;

import java.util.List;

public final class ParsedSlashCommand {

    private final boolean command;
    private final String name;
    private final List<String> args;
    private final String error;

    private ParsedSlashCommand(boolean command, String name, List<String> args, String error) {
        this.command = command;
        this.name = name;
        this.args = args;
        this.error = error;
    }

    public static ParsedSlashCommand notCommand() {
        return new ParsedSlashCommand(false, null, List.of(), null);
    }

    public static ParsedSlashCommand success(String name, List<String> args) {
        return new ParsedSlashCommand(true, name, List.copyOf(args), null);
    }

    public static ParsedSlashCommand error(String error) {
        return new ParsedSlashCommand(true, null, List.of(), error);
    }

    public boolean isCommand() {
        return command;
    }

    public String getName() {
        return name;
    }

    public List<String> getArgs() {
        return args;
    }

    public boolean hasError() {
        return error != null && !error.isBlank();
    }

    public String getError() {
        return error;
    }
}
