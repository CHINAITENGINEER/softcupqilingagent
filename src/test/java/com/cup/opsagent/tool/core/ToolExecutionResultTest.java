package com.cup.opsagent.tool.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutionResultTest {

    @Test
    void shouldRejectNullToolName() {
        assertThatThrownBy(() -> new ToolExecutionResult(null, false, "", "failed", null, 10, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
    }

    @Test
    void shouldRejectBlankToolName() {
        assertThatThrownBy(() -> new ToolExecutionResult(" ", false, "", "failed", null, 10, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
    }

    @Test
    void shouldRejectNegativeDurationMs() {
        assertThatThrownBy(() -> new ToolExecutionResult("test_tool", false, "", "failed", null, -1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");
    }

    @Test
    void shouldRejectFailureCodeWhenSuccessIsTrue() {
        assertThatThrownBy(() -> new ToolExecutionResult(
                "test_tool",
                true,
                "",
                "",
                0,
                10,
                Instant.now(),
                ToolExecutionFailureCode.SHELL_WRAPPER_DENIED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureCode");
    }

    @Test
    void shouldAllowFailureCodeWhenSuccessIsFalse() {
        ToolExecutionResult result = new ToolExecutionResult(
                "test_tool",
                false,
                "",
                "denied",
                null,
                10,
                Instant.now(),
                ToolExecutionFailureCode.SHELL_WRAPPER_DENIED
        );

        assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
    }

    @Test
    void shouldKeepSevenArgConstructorFailureCodeNull() {
        ToolExecutionResult result = new ToolExecutionResult("test_tool", false, "", "failed", null, 10, Instant.now());

        assertThat(result.failureCode()).isNull();
    }

    @Test
    void shouldNormalizeNullStdoutAndStderr() {
        ToolExecutionResult result = new ToolExecutionResult("test_tool", false, null, null, null, 10, Instant.now());

        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void shouldDefaultExecutedAtWhenNull() {
        ToolExecutionResult result = new ToolExecutionResult("test_tool", false, "", "failed", null, 10, null);

        assertThat(result.executedAt()).isNotNull();
    }
}
