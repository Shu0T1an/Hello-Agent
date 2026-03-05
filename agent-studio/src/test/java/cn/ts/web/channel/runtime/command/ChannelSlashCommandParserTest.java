package cn.ts.web.channel.runtime.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelSlashCommandParserTest {

    private final ChannelSlashCommandParser parser = new ChannelSlashCommandParser();

    @Test
    void parse_ShouldReturnNotCommand_WhenTextNotStartingWithSlash() {
        ParsedSlashCommand parsed = parser.parse("hello");
        assertFalse(parsed.isCommand());
    }

    @Test
    void parse_ShouldParseCommandNameAndArgs() {
        ParsedSlashCommand parsed = parser.parse("/clear foo \"bar baz\" 'x y'");
        assertTrue(parsed.isCommand());
        assertFalse(parsed.hasError());
        assertEquals("clear", parsed.getName());
        assertEquals(List.of("foo", "bar baz", "x y"), parsed.getArgs());
    }

    @Test
    void parse_ShouldHandleEscapedQuotesInsideQuotedArgument() {
        ParsedSlashCommand parsed = parser.parse("/clear \"a \\\"quoted\\\" value\"");
        assertTrue(parsed.isCommand());
        assertFalse(parsed.hasError());
        assertEquals(List.of("a \"quoted\" value"), parsed.getArgs());
    }

    @Test
    void parse_ShouldReturnError_WhenQuoteNotClosed() {
        ParsedSlashCommand parsed = parser.parse("/clear \"not closed");
        assertTrue(parsed.isCommand());
        assertTrue(parsed.hasError());
    }
}
