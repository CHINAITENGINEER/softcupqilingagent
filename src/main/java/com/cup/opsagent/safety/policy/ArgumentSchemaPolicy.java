package com.cup.opsagent.safety.policy;

import com.cup.opsagent.safety.Policy;
import com.cup.opsagent.safety.PolicyContext;
import com.cup.opsagent.safety.PolicyDecision;
import com.cup.opsagent.safety.PolicyHit;
import com.cup.opsagent.safety.validation.NumericArgumentValidator;
import com.cup.opsagent.safety.validation.ServiceNameValidator;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Order(30)
public class ArgumentSchemaPolicy implements Policy {

    private static final Pattern INJECTION_PATTERN = Pattern.compile("(;|&&|\\|\\||\\||`|\\$\\(|>|<|\\n|\\r)");

    @Override
    public String name() {
        return "ArgumentSchemaPolicy";
    }

    @Override
    public Optional<PolicyDecision> evaluate(PolicyContext context) {
        if (context.toolCall() == null || context.toolDefinition().isEmpty()) {
            return Optional.empty();
        }
        String toolName = context.toolCall().toolName();
        Map<String, Object> arguments = context.toolCall().arguments() == null ? Map.of() : context.toolCall().arguments();

        Optional<String> validationError = switch (toolName) {
            case PortUsageTool.NAME -> validatePort(arguments);
            case ServiceStatusTool.NAME, RestartServiceTool.NAME -> validateServiceName(arguments);
            case TopProcessesTool.NAME -> validateOptionalLimit(arguments);
            default -> validateNoInjectedArgument(arguments);
        };

        return validationError.map(this::block);
    }

    private Optional<String> validatePort(Map<String, Object> arguments) {
        Object rawPort = arguments.get("port");
        Optional<Integer> port = NumericArgumentValidator.integerInRange(rawPort, 1, 65535);
        if (port.isEmpty()) {
            return Optional.of("port must be an integer between 1 and 65535");
        }
        return Optional.empty();
    }

    private Optional<String> validateServiceName(Map<String, Object> arguments) {
        return ServiceNameValidator.validate(arguments.get("serviceName"));
    }

    private Optional<String> validateOptionalLimit(Map<String, Object> arguments) {
        if (!arguments.containsKey("limit")) {
            return Optional.empty();
        }
        Optional<Integer> limit = NumericArgumentValidator.integerInRange(arguments.get("limit"), 1, 50);
        if (limit.isEmpty()) {
            return Optional.of("limit must be an integer between 1 and 50");
        }
        return Optional.empty();
    }

    private Optional<String> validateNoInjectedArgument(Map<String, Object> arguments) {
        for (Object value : arguments.values()) {
            if (value instanceof String text && INJECTION_PATTERN.matcher(text).find()) {
                return Optional.of("argument contains shell injection characters");
            }
        }
        return Optional.empty();
    }

    private PolicyDecision block(String reason) {
        return PolicyDecision.block(
                RiskLevel.HIGH,
                reason,
                List.of(new PolicyHit(name(), "HIGH", reason)),
                List.of("请检查参数格式，避免命令连接符、路径穿越或非法字符")
        );
    }
}
