package com.cup.opsagent.executor;

import com.cup.opsagent.tool.core.ToolExecutionFailureCode;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class CommandRunner {

    private static final Set<String> DENIED_SHELL_EXECUTABLES = Set.of(
            "sh",
            "bash",
            "zsh",
            "dash",
            "ksh",
            "cmd",
            "cmd.exe",
            "powershell",
            "powershell.exe",
            "pwsh",
            "pwsh.exe"
    );
    private final CommandTemplateRegistry commandTemplateRegistry;
    private final ProcessLauncher processLauncher;

    @Autowired
    public CommandRunner(CommandTemplateRegistry commandTemplateRegistry) {
        this(commandTemplateRegistry, new ProcessBuilderProcessLauncher());
    }

    CommandRunner(CommandTemplateRegistry commandTemplateRegistry, ProcessLauncher processLauncher) {
        this.commandTemplateRegistry = Objects.requireNonNull(commandTemplateRegistry, "commandTemplateRegistry must not be null");
        this.processLauncher = Objects.requireNonNull(processLauncher, "processLauncher must not be null");
    }

    public ToolExecutionResult run(
            String toolName,
            CommandTemplateId templateId,
            Map<String, Object> args,
            CommandExecutionOptions options
    ) {
        Instant startedAt = Instant.now();
        if (templateId == null) {
            return result(toolName, false, "", "command template id is required", null, startedAt, ToolExecutionFailureCode.COMMAND_TEMPLATE_ID_MISSING);
        }
        if (options == null) {
            return result(toolName, false, "", "command execution options are required", null, startedAt, ToolExecutionFailureCode.COMMAND_EXECUTION_OPTIONS_MISSING);
        }
        CommandSpec spec;
        try {
            spec = commandTemplateRegistry.build(templateId, args, options.timeoutMs(), options.outputLimitBytes());
        } catch (IllegalArgumentException exception) {
            return result(toolName, false, "", "invalid command template args: " + exception.getMessage(), null, startedAt, ToolExecutionFailureCode.INVALID_COMMAND_TEMPLATE_ARGS);
        }
        return run(toolName, spec, startedAt);
    }

    ToolExecutionResult run(String toolName, CommandSpec spec) {
        return run(toolName, spec, Instant.now());
    }

    private ToolExecutionResult run(String toolName, CommandSpec spec, Instant startedAt) {
        if (spec == null) {
            return result(toolName, false, "", "command spec is required", null, startedAt, ToolExecutionFailureCode.COMMAND_SPEC_MISSING);
        }
        if (usesDeniedShellWrapper(spec.command())) {
            return result(toolName, false, "", "denied shell wrapper command", null, startedAt, ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
        }
        if (!commandTemplateRegistry.matches(spec)) {
            return result(toolName, false, "", "command does not match template: " + spec.templateId(), null, startedAt, ToolExecutionFailureCode.COMMAND_TEMPLATE_MISMATCH);
        }

        Process process = null;
        try {
            process = processLauncher.start(spec.command());
            Process currentProcess = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readLimitedSafely(currentProcess.getInputStream(), spec.outputLimitBytes()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readLimitedSafely(currentProcess.getErrorStream(), spec.outputLimitBytes()));

            boolean completed = currentProcess.waitFor(spec.timeoutMs(), TimeUnit.MILLISECONDS);
            if (!completed) {
                currentProcess.destroyForcibly();
                return result(toolName, false, stdoutFuture.getNow(""), "command timed out", null, startedAt, ToolExecutionFailureCode.COMMAND_TIMEOUT);
            }

            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            int exitCode = currentProcess.exitValue();
            return result(toolName, exitCode == 0, stdout, stderr, exitCode, startedAt);
        } catch (IOException exception) {
            return result(toolName, false, "", safeMessage(exception, "process start failed"), null, startedAt, ToolExecutionFailureCode.PROCESS_START_FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(toolName, false, "", "command interrupted", null, startedAt, ToolExecutionFailureCode.COMMAND_INTERRUPTED);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean usesDeniedShellWrapper(List<String> command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        return DENIED_SHELL_EXECUTABLES.contains(executableName(command.get(0)));
    }

    private String executableName(String value) {
        String normalized = normalize(value).replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalized.length() - 1) {
            return normalized.substring(lastSlash + 1);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ToolExecutionResult result(
            String toolName,
            boolean success,
            String stdout,
            String stderr,
            Integer exitCode,
            Instant startedAt
    ) {
        return result(toolName, success, stdout, stderr, exitCode, startedAt, null);
    }

    private ToolExecutionResult result(
            String toolName,
            boolean success,
            String stdout,
            String stderr,
            Integer exitCode,
            Instant startedAt,
            ToolExecutionFailureCode failureCode
    ) {
        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
        return new ToolExecutionResult(toolName, success, stdout, stderr, exitCode, durationMs, Instant.now(), failureCode);
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private String readLimitedSafely(InputStream inputStream, int limitBytes) {
        try {
            return readLimited(inputStream, limitBytes);
        } catch (IOException exception) {
            return "failed to read process output: " + exception.getMessage();
        }
    }

    private String readLimited(InputStream inputStream, int limitBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int total = 0;
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            int remaining = limitBytes - total;
            if (remaining <= 0) {
                break;
            }
            int accepted = Math.min(read, remaining);
            buffer.write(chunk, 0, accepted);
            total += accepted;
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        if (total >= limitBytes) {
            return text + "\n[output truncated]";
        }
        return text;
    }
}
