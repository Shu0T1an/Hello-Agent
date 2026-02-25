package cn.ts.agent.tool.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellOutputLimiterTest {

    @Test
    void truncateByLineAndByte() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            source.append("line-").append(i).append('\n');
        }

        ShellOutputLimiter.LimitResult result = ShellOutputLimiter.limit(
                source.toString(),
                10,
                64
        );

        assertTrue(result.truncated());
        assertTrue(result.content().length() <= 64);
    }
}

