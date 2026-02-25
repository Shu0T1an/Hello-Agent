package cn.ts.agent.tool.shell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellSessionManagerTest {

    private final ShellToolConfig config = ShellToolConfig.builder()
            .defaultTimeoutSeconds(2)
            .maxTimeoutSeconds(5)
            .maxOutputLines(200)
            .maxOutputBytes(16 * 1024)
            .idleTtlSeconds(60)
            .autoRestartOnTimeout(true)
            .allowedWorkingDirectories(Path.of(System.getProperty("user.dir")).toString())
            .build();

    private final ShellSessionManager manager = new ShellSessionManager(config);

    @AfterEach
    void tearDown() {
        manager.closeAll();
    }

    @Test
    void preservesSessionContextAcrossCalls() throws Exception {
        Path root = Files.createTempDirectory("shelltool-test");
        Path child = Files.createDirectories(root.resolve("child"));

        String cdCommand = isWindows()
                ? "Set-Location '" + child.toString().replace("'", "''") + "'"
                : "cd '" + child.toString().replace("'", "'\\''") + "'";

        ShellCommandResult step1 = manager.executeCommand("s1", cdCommand, null, null, false);
        ShellCommandResult step2 = manager.executeCommand("s1", pwdCommand(), null, null, false);

        assertTrue(step1.success());
        assertTrue(step2.success());
        assertTrue(step2.stdout().replace("\\", "/").toLowerCase().contains(child.toString().replace("\\", "/").toLowerCase()));
    }

    @Test
    void timeoutRestartsSessionWhenEnabled() {
        ShellCommandResult timeout = manager.executeCommand("s2", sleepCommand(3), 1, null, false);
        ShellCommandResult probe = manager.executeCommand("s2", echoCommand("ok"), null, null, false);

        assertTrue(timeout.timedOut());
        assertTrue(timeout.sessionRestarted());
        assertTrue(probe.success());
        assertTrue(probe.stdout().toLowerCase().contains("ok"));
    }

    @Test
    void restartResetsSessionState() throws Exception {
        Path root = Files.createTempDirectory("shelltool-restart");
        Path child = Files.createDirectories(root.resolve("child2"));

        String cdCommand = isWindows()
                ? "Set-Location '" + child.toString().replace("'", "''") + "'"
                : "cd '" + child.toString().replace("'", "'\\''") + "'";

        ShellCommandResult before = manager.executeCommand("s3", cdCommand + "\n" + pwdCommand(), null, null, false);
        ShellCommandResult after = manager.executeCommand("s3", pwdCommand(), null, null, true);

        String normalizedChild = child.toString().replace("\\", "/").toLowerCase();
        assertTrue(before.stdout().replace("\\", "/").toLowerCase().contains(normalizedChild));
        assertFalse(after.stdout().replace("\\", "/").toLowerCase().contains(normalizedChild));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private static String pwdCommand() {
        return isWindows() ? "$PWD.Path" : "pwd";
    }

    private static String sleepCommand(int seconds) {
        return isWindows() ? "Start-Sleep -Seconds " + seconds : "sleep " + seconds;
    }

    private static String echoCommand(String text) {
        return "echo " + text;
    }
}

