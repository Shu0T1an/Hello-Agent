package cn.ts.agent.tool.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public class ShellSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(ShellSessionManager.class);

    private static final String ERROR_INVALID_COMMAND = "SHELL_INVALID_COMMAND";
    private static final String ERROR_BLOCKED_COMMAND = "SHELL_BLOCKED_COMMAND";
    private static final String ERROR_WORKDIR_DENIED = "SHELL_WORKDIR_DENIED";
    private static final String ERROR_INIT_FAILED = "SHELL_INIT_FAILED";
    private static final String ERROR_EXEC_FAILED = "SHELL_EXEC_FAILED";

    private final ShellToolConfig config;
    private final ConcurrentHashMap<String, ShellSession> sessions = new ConcurrentHashMap<>();
    private final boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
    private final Pattern[] blockedPatterns;

    public ShellSessionManager(ShellToolConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.blockedPatterns = config.blockedCommandPatterns().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .toArray(Pattern[]::new);
    }

    public ShellCommandResult executeCommand(String sessionKey,
                                             String command,
                                             Integer timeoutSeconds,
                                             String workingDirectory,
                                             boolean restart) {
        Instant start = Instant.now();
        String key = normalizeSessionKey(sessionKey);
        String userCommand = validateCommand(command);
        ensureCommandAllowed(userCommand);
        Path normalizedWorkingDir = validateWorkingDirectory(workingDirectory);

        int effectiveTimeout = resolveTimeoutSeconds(timeoutSeconds);
        boolean sessionRestarted = false;

        if (restart) {
            restartSession(key);
            sessionRestarted = true;
        }

        ShellSession session = sessions.computeIfAbsent(key, ignored -> createSession(key));

        session.lock.lock();
        try {
            if (!session.isAlive()) {
                closeSession(session);
                session = createSession(key);
                sessions.put(key, session);
                sessionRestarted = true;
            }

            String marker = "__HA_MARKER__" + UUID.randomUUID();
            String fullCommand = buildCommand(userCommand, normalizedWorkingDir);
            writeCommand(session, fullCommand, marker);

            ReadResult read = readUntilMarker(session, marker, effectiveTimeout);

            if (read.timedOut) {
                if (config.autoRestartOnTimeout()) {
                    closeSession(session);
                    ShellSession replacement = createSession(key);
                    sessions.put(key, replacement);
                    sessionRestarted = true;
                }
                long durationMs = Duration.between(start, Instant.now()).toMillis();
                return new ShellCommandResult(
                        false, -1, true, read.truncated(), read.stdout, read.stderr,
                        durationMs, sessionRestarted, "SHELL_TIMEOUT");
            }

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            return new ShellCommandResult(
                    read.exitCode == 0,
                    read.exitCode,
                    false,
                    read.truncated(),
                    read.stdout,
                    read.stderr,
                    durationMs,
                    sessionRestarted,
                    null
            );
        } catch (ShellToolException e) {
            throw e;
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            logger.error("Shell execution failed, session={}", key, e);
            return new ShellCommandResult(
                    false, -1, false, false, "", e.getMessage(),
                    durationMs, sessionRestarted, ERROR_EXEC_FAILED
            );
        } finally {
            ShellSession current = sessions.get(key);
            if (current != null) {
                current.lastActiveAt = System.currentTimeMillis();
            }
            session.lock.unlock();
        }
    }

    public void cleanupIdleSessions() {
        long ttlMs = TimeUnit.SECONDS.toMillis(config.idleTtlSeconds());
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ShellSession> entry : sessions.entrySet()) {
            ShellSession session = entry.getValue();
            if (now - session.lastActiveAt > ttlMs) {
                if (sessions.remove(entry.getKey(), session)) {
                    closeSession(session);
                }
            }
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public void closeAll() {
        for (Map.Entry<String, ShellSession> entry : sessions.entrySet()) {
            closeSession(entry.getValue());
        }
        sessions.clear();
    }

    public void restartSession(String sessionKey) {
        String key = normalizeSessionKey(sessionKey);
        ShellSession old = sessions.remove(key);
        if (old != null) {
            closeSession(old);
        }
    }

    private String normalizeSessionKey(String key) {
        if (key == null || key.isBlank()) {
            return "global";
        }
        return key.trim();
    }

    private int resolveTimeoutSeconds(Integer timeoutSeconds) {
        int timeout = timeoutSeconds == null ? config.defaultTimeoutSeconds() : timeoutSeconds;
        if (timeout <= 0) {
            timeout = config.defaultTimeoutSeconds();
        }
        return Math.min(timeout, config.maxTimeoutSeconds());
    }

    private String validateCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new ShellToolException(ERROR_INVALID_COMMAND, "command is empty");
        }
        return command;
    }

    private void ensureCommandAllowed(String command) {
        for (Pattern blockedPattern : blockedPatterns) {
            if (blockedPattern.matcher(command).find()) {
                throw new ShellToolException(ERROR_BLOCKED_COMMAND, "command blocked by security policy");
            }
        }
    }

    private Path validateWorkingDirectory(String workingDirectory) {
        if (workingDirectory == null || workingDirectory.isBlank()) {
            return null;
        }
        Path normalized = Path.of(workingDirectory).toAbsolutePath().normalize();
        var roots = config.normalizedAllowedRoots();
        if (roots.isEmpty()) {
            return normalized;
        }

        boolean allowed = roots.stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new ShellToolException(ERROR_WORKDIR_DENIED, "workingDirectory is outside allowed roots");
        }
        return normalized;
    }

    private String buildCommand(String command, Path workingDirectory) {
        if (workingDirectory == null) {
            return command;
        }
        if (windows) {
            return "Set-Location -LiteralPath '" + escapePsSingleQuote(workingDirectory.toString()) + "'\n" + command;
        }
        return "cd '" + escapeBashSingleQuote(workingDirectory.toString()) + "'\n" + command;
    }

    private ShellSession createSession(String sessionKey) {
        try {
            ProcessBuilder builder = windows
                    ? new ProcessBuilder("powershell.exe", "-NoProfile", "-NoLogo", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", "-")
                    : new ProcessBuilder("/bin/bash", "-l");
            builder.redirectErrorStream(false);
            Process process = builder.start();

            ShellSession session = new ShellSession(
                    sessionKey,
                    process,
                    new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))
            );

            session.startReader(process.getInputStream(), session.stdoutQueue, "stdout");
            session.startReader(process.getErrorStream(), session.stderrQueue, "stderr");
            session.lastActiveAt = System.currentTimeMillis();
            return session;
        } catch (IOException e) {
            throw new ShellToolException(ERROR_INIT_FAILED, "failed to start shell process", e);
        }
    }

    private void writeCommand(ShellSession session, String command, String marker) throws IOException {
        session.stdin.write(command);
        session.stdin.newLine();
        session.stdin.write(markerCommand(marker));
        session.stdin.newLine();
        session.stdin.flush();
    }

    private String markerCommand(String marker) {
        if (windows) {
            return "Write-Output \"" + marker + " $LASTEXITCODE\"";
        }
        return "printf '" + marker + " %s\\n' $?";
    }

    private ReadResult readUntilMarker(ShellSession session, String marker, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Integer exitCode = null;

        while (System.nanoTime() < deadline) {
            String outLine = session.stdoutQueue.poll(100, TimeUnit.MILLISECONDS);
            if (outLine != null) {
                Integer parsed = parseMarker(marker, outLine);
                if (parsed != null) {
                    exitCode = parsed;
                    break;
                }
                stdout.append(outLine).append(System.lineSeparator());
            }
            drainStderr(session.stderrQueue, stderr);
        }

        drainStderr(session.stderrQueue, stderr);

        ShellOutputLimiter.LimitResult stdoutLimited =
                ShellOutputLimiter.limit(stdout.toString(), config.maxOutputLines(), config.maxOutputBytes());
        ShellOutputLimiter.LimitResult stderrLimited =
                ShellOutputLimiter.limit(stderr.toString(), config.maxOutputLines(), config.maxOutputBytes());

        boolean timedOut = exitCode == null;
        boolean truncated = stdoutLimited.truncated() || stderrLimited.truncated();
        return new ReadResult(
                timedOut ? -1 : exitCode,
                timedOut,
                stdoutLimited.content(),
                stderrLimited.content(),
                truncated
        );
    }

    private void drainStderr(BlockingQueue<String> queue, StringBuilder stderr) {
        String line;
        while ((line = queue.poll()) != null) {
            stderr.append(line).append(System.lineSeparator());
        }
    }

    private Integer parseMarker(String marker, String line) {
        if (line == null || !line.startsWith(marker)) {
            return null;
        }
        String remains = line.substring(marker.length()).trim();
        if (remains.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(remains.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void closeSession(ShellSession session) {
        try {
            session.stdin.close();
        } catch (IOException ignored) {
        }
        session.process.destroy();
        try {
            if (!session.process.waitFor(2, TimeUnit.SECONDS)) {
                session.process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            session.process.destroyForcibly();
        }
    }

    private static String escapePsSingleQuote(String input) {
        return input.replace("'", "''");
    }

    private static String escapeBashSingleQuote(String input) {
        return input.replace("'", "'\\''");
    }

    private record ReadResult(int exitCode,
                              boolean timedOut,
                              String stdout,
                              String stderr,
                              boolean truncated) {
    }

    private static final class ShellSession {
        private final String key;
        private final Process process;
        private final BufferedWriter stdin;
        private final ReentrantLock lock = new ReentrantLock();
        private final BlockingQueue<String> stdoutQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> stderrQueue = new LinkedBlockingQueue<>();
        private volatile long lastActiveAt;

        private ShellSession(String key, Process process, BufferedWriter stdin) {
            this.key = key;
            this.process = process;
            this.stdin = stdin;
        }

        private boolean isAlive() {
            return process.isAlive();
        }

        private void startReader(InputStream inputStream, BlockingQueue<String> queue, String streamName) {
            Thread thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        queue.offer(line);
                    }
                } catch (IOException e) {
                    logger.debug("Shell session stream reader closed, key={}, stream={}, message={}", key, streamName, e.getMessage());
                }
            }, "shelltool-" + key + "-" + streamName);
            thread.setDaemon(true);
            thread.start();
        }
    }
}

