package cn.ts.web.tool.local;

import cn.ts.agent.tool.shell.ShellToolConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellToolPropertiesTest {

    @Test
    void mapsPropertiesToCoreConfig() {
        ShellToolProperties properties = new ShellToolProperties();
        properties.setDefaultTimeoutSeconds(11);
        properties.setMaxTimeoutSeconds(22);
        properties.setMaxOutputLines(33);
        properties.setMaxOutputBytes(44);
        properties.setIdleTtlSeconds(55);
        properties.setAutoRestartOnTimeout(false);
        properties.setAllowedWorkingDirectories(List.of("D:/JavaProject/Hello-Agent"));
        properties.setBlockedCommandPatterns(List.of("(?i)test"));

        ShellToolConfig config = properties.toCoreConfig();
        assertEquals(11, config.defaultTimeoutSeconds());
        assertEquals(22, config.maxTimeoutSeconds());
        assertEquals(33, config.maxOutputLines());
        assertEquals(44, config.maxOutputBytes());
        assertEquals(55, config.idleTtlSeconds());
        assertTrue(!config.autoRestartOnTimeout());
        assertEquals(1, config.allowedWorkingDirectories().size());
        assertEquals(1, config.blockedCommandPatterns().size());
    }
}

