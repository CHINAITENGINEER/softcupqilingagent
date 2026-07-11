package com.cup.opsagent.executor;

import com.cup.opsagent.tool.core.ToolExecutionFailureCode;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandRunnerTest {

    private final CommandRunner commandRunner = new CommandRunner(new CommandTemplateRegistry());

    @Test
    void shouldDenyDirectShellWrapper() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.GET_SYSTEM_LOAD,
                List.of("sh", "-c", "echo unsafe"),
                1000,
                1024
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("denied shell wrapper command");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
    }

    @Test
    void shouldDenyShellWrapperWithAbsolutePath() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.GET_SYSTEM_LOAD,
                List.of("/bin/sh", "-c", "echo unsafe"),
                1000,
                1024
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("denied shell wrapper command");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
    }

    @Test
    void shouldDenyWindowsShellWrapperWithAbsolutePath() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.GET_SYSTEM_LOAD,
                List.of("C:\\Windows\\System32\\cmd.exe", "/c", "echo unsafe"),
                1000,
                1024
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("denied shell wrapper command");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
    }

    @Test
    void shouldDenyPowershellEvenWithPreOptions() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.GET_SYSTEM_LOAD,
                List.of("powershell", "-NoProfile", "-Command", "Write-Output unsafe"),
                1000,
                1024
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("denied shell wrapper command");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
    }

    @Test
    void shouldDenyCommandThatDoesNotMatchTemplate() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.GET_SYSTEM_LOAD,
                List.of("python", "-c", "print('unsafe')"),
                1000,
                1024
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("command does not match template");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_TEMPLATE_MISMATCH);
    }

    @Test
    void shouldAllowKnownPortUsageTemplateShape() {
        ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
                CommandTemplateId.CHECK_PORT_USAGE,
                List.of("ss", "-tulpn", "sport", "=", ":22"),
                1000,
                1024
        ));

        assertThat(result.stderr()).doesNotContain("command does not match template");
    }

    @Test
    void shouldRejectNullTemplateId() {
        assertThatThrownBy(() -> new CommandSpec(null, List.of("uptime"), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateId");
    }

    @Test
    void shouldCreateCommandExecutionOptions() {
        CommandExecutionOptions options = new CommandExecutionOptions(1000, 1024);

        assertThat(options.timeoutMs()).isEqualTo(1000);
        assertThat(options.outputLimitBytes()).isEqualTo(1024);
    }

    @Test
    void shouldRejectInvalidCommandExecutionOptions() {
        assertThatThrownBy(() -> new CommandExecutionOptions(0, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeoutMs");
        assertThatThrownBy(() -> new CommandExecutionOptions(1000, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputLimitBytes");
    }

    @Test
    void runWithTemplateShouldBuildAndPassTemplateValidation() {
        ToolExecutionResult result = commandRunner.run(
                "test_tool",
                CommandTemplateId.GET_SYSTEM_LOAD,
                Map.of(),
                new CommandExecutionOptions(1000, 1024)
        );

        assertThat(result.stderr()).doesNotContain("invalid command template args");
        assertThat(result.stderr()).doesNotContain("command does not match template");
    }

    @Test
    void runWithTemplateShouldReturnFailureForInvalidArgs() {
        ToolExecutionResult result = commandRunner.run(
                "test_tool",
                CommandTemplateId.CHECK_PORT_USAGE,
                Map.of(),
                new CommandExecutionOptions(1000, 1024)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("invalid command template args");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.INVALID_COMMAND_TEMPLATE_ARGS);
    }

    @Test
    void runWithTemplateShouldReturnFailureForUnexpectedArgs() {
        ToolExecutionResult result = commandRunner.run(
                "test_tool",
                CommandTemplateId.GET_SYSTEM_LOAD,
                Map.of("unexpected", true),
                new CommandExecutionOptions(1000, 1024)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("invalid command template args");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.INVALID_COMMAND_TEMPLATE_ARGS);
        assertThat(result.stderr()).contains("unexpected command template args");
    }

    @Test
    void runWithTemplateShouldReturnFailureForNullTemplateId() {
        ToolExecutionResult result = commandRunner.run("test_tool", null, Map.of(), new CommandExecutionOptions(1000, 1024));

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("command template id is required");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_TEMPLATE_ID_MISSING);
    }

    @Test
    void runWithTemplateShouldReturnFailureForNullOptions() {
        ToolExecutionResult result = commandRunner.run("test_tool", CommandTemplateId.GET_SYSTEM_LOAD, Map.of(), null);

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("command execution options are required");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_EXECUTION_OPTIONS_MISSING);
    }

    @Test
    void shouldRejectNullCommandTemplateRegistryInConstructor() {
        assertThatThrownBy(() -> new CommandRunner(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("commandTemplateRegistry");
    }

    @Test
    void shouldReturnFailureForNullCommandSpec() {
        ToolExecutionResult result = commandRunner.run("test_tool", null);

        assertThat(result.success()).isFalse();
        assertThat(result.stderr()).contains("command spec is required");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_SPEC_MISSING);
    }

    @Test
    void shouldRejectNullProcessLauncherInTestConstructor() {
        assertThatThrownBy(() -> new CommandRunner(new CommandTemplateRegistry(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processLauncher");
    }

    @Test
    void shouldSetFailureCodeForProcessStartFailed() {
        CommandRunner runner = new CommandRunner(new CommandTemplateRegistry(), command -> {
            throw new IOException("boom");
        });

        ToolExecutionResult result = runner.run("test_tool", canonicalSystemLoadSpec());

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).isEqualTo("boom");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.PROCESS_START_FAILED);
    }

    @Test
    void shouldSetFailureCodeForCommandTimeout() {
        FakeProcess process = new FakeProcess(false, false, 0, "partial-output", "");
        CommandRunner runner = new CommandRunner(new CommandTemplateRegistry(), command -> process);

        ToolExecutionResult result = runner.run("test_tool", canonicalSystemLoadSpec());

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("command timed out");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_TIMEOUT);
        assertThat(process.destroyForciblyCalled).isTrue();
    }

    @Test
    void shouldSetFailureCodeForCommandInterrupted() {
        FakeProcess process = new FakeProcess(true, true, 0, "", "");
        CommandRunner runner = new CommandRunner(new CommandTemplateRegistry(), command -> process);

        ToolExecutionResult result = runner.run("test_tool", canonicalSystemLoadSpec());

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isNull();
        assertThat(result.stderr()).contains("command interrupted");
        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.COMMAND_INTERRUPTED);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void shouldDefensivelyCopyCommandList() {
        List<String> command = new ArrayList<>(List.of("uptime"));
        CommandSpec spec = new CommandSpec(CommandTemplateId.GET_SYSTEM_LOAD, command, 1000, 1024);

        command.set(0, "python");

        assertThat(spec.command()).containsExactly("uptime");
    }

    @Test
    void shouldRejectNullCommandElement() {
        List<String> command = new ArrayList<>();
        command.add("uptime");
        command.add(null);

        assertThatThrownBy(() -> new CommandSpec(CommandTemplateId.GET_SYSTEM_LOAD, command, 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command elements");
    }

    @Test
    void shouldRejectBlankCommandElement() {
        assertThatThrownBy(() -> new CommandSpec(CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime", " "), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command elements");
    }

    private CommandSpec canonicalSystemLoadSpec() {
        return new CommandSpec(CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime"), 1000, 1024);
    }

    private static class FakeProcess extends Process {
        private final boolean waitCompleted;
        private final boolean interruptOnWait;
        private final int exitCode;
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr;
        private boolean destroyForciblyCalled;

        FakeProcess(boolean waitCompleted, boolean interruptOnWait, int exitCode, String stdout, String stderr) {
            this.waitCompleted = waitCompleted;
            this.interruptOnWait = interruptOnWait;
            this.exitCode = exitCode;
            this.stdout = new ByteArrayInputStream(stdout.getBytes());
            this.stderr = new ByteArrayInputStream(stderr.getBytes());
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return stdout;
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            if (interruptOnWait) {
                throw new InterruptedException("interrupted");
            }
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interruptOnWait) {
                throw new InterruptedException("interrupted");
            }
            return waitCompleted;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyForciblyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !waitCompleted && !destroyForciblyCalled;
        }
    }
}
