package com.cup.opsagent.executor;

import com.cup.opsagent.safety.validation.NumericArgumentValidator;
import com.cup.opsagent.safety.validation.ServiceNameValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CommandTemplateRegistry {

    private static final Pattern PORT_ARGUMENT_PATTERN = Pattern.compile("^:([1-9][0-9]{0,4})$");

    CommandSpec build(
            CommandTemplateId templateId,
            Map<String, Object> args,
            long timeoutMs,
            int outputLimitBytes
    ) {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId must not be null");
        }
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        return switch (templateId) {
            case GET_SYSTEM_LOAD -> {
                requireOnlyArgs(safeArgs);
                yield getSystemLoad(timeoutMs, outputLimitBytes);
            }
            case GET_TOP_PROCESSES -> {
                requireOnlyArgs(safeArgs);
                yield getTopProcesses(timeoutMs, outputLimitBytes);
            }
            case GET_OPEN_PORTS -> {
                requireOnlyArgs(safeArgs);
                yield getOpenPorts(timeoutMs, outputLimitBytes);
            }
            case CHECK_PORT_USAGE -> {
                requireOnlyArgs(safeArgs, "port");
                yield checkPortUsage(requiredPort(safeArgs), timeoutMs, outputLimitBytes);
            }
            case GET_SERVICE_STATUS -> {
                requireOnlyArgs(safeArgs, "serviceName");
                yield getServiceStatus(requiredServiceName(safeArgs), timeoutMs, outputLimitBytes);
            }
            case RESTART_SERVICE -> {
                requireOnlyArgs(safeArgs, "serviceName");
                yield restartService(requiredServiceName(safeArgs), timeoutMs, outputLimitBytes);
            }
            case VERIFY_SERVICE_ACTIVE -> {
                requireOnlyArgs(safeArgs, "serviceName");
                yield verifyServiceActive(requiredServiceName(safeArgs), timeoutMs, outputLimitBytes);
            }
        };
    }

    CommandSpec getSystemLoad(long timeoutMs, int outputLimitBytes) {
        return checked(new CommandSpec(CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime"), timeoutMs, outputLimitBytes));
    }

    CommandSpec getTopProcesses(long timeoutMs, int outputLimitBytes) {
        return checked(new CommandSpec(
                CommandTemplateId.GET_TOP_PROCESSES,
                List.of("ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"),
                timeoutMs,
                outputLimitBytes
        ));
    }

    CommandSpec getOpenPorts(long timeoutMs, int outputLimitBytes) {
        return checked(new CommandSpec(CommandTemplateId.GET_OPEN_PORTS, List.of("ss", "-tulpn"), timeoutMs, outputLimitBytes));
    }

    CommandSpec checkPortUsage(int port, long timeoutMs, int outputLimitBytes) {
        int safePort = NumericArgumentValidator.integerInRange(port, 1, 65535)
                .orElseThrow(() -> new IllegalArgumentException("port must be an integer between 1 and 65535"));
        return checked(new CommandSpec(
                CommandTemplateId.CHECK_PORT_USAGE,
                List.of("ss", "-tulpn", "sport", "=", ":" + safePort),
                timeoutMs,
                outputLimitBytes
        ));
    }

    CommandSpec getServiceStatus(String serviceName, long timeoutMs, int outputLimitBytes) {
        String safeServiceName = ServiceNameValidator.extract(serviceName)
                .orElseThrow(() -> new IllegalArgumentException("serviceName is invalid"));
        return checked(new CommandSpec(
                CommandTemplateId.GET_SERVICE_STATUS,
                List.of("systemctl", "--no-pager", "status", "--", safeServiceName),
                timeoutMs,
                outputLimitBytes
        ));
    }

    CommandSpec restartService(String serviceName, long timeoutMs, int outputLimitBytes) {
        String safeServiceName = ServiceNameValidator.extract(serviceName)
                .orElseThrow(() -> new IllegalArgumentException("serviceName is invalid"));
        return checked(new CommandSpec(
                CommandTemplateId.RESTART_SERVICE,
                List.of("systemctl", "restart", "--", safeServiceName),
                timeoutMs,
                outputLimitBytes
        ));
    }

    CommandSpec verifyServiceActive(String serviceName, long timeoutMs, int outputLimitBytes) {
        String safeServiceName = ServiceNameValidator.extract(serviceName)
                .orElseThrow(() -> new IllegalArgumentException("serviceName is invalid"));
        return checked(new CommandSpec(
                CommandTemplateId.VERIFY_SERVICE_ACTIVE,
                List.of("systemctl", "is-active", "--", safeServiceName),
                timeoutMs,
                outputLimitBytes
        ));
    }

    boolean matches(CommandSpec spec) {
        if (spec == null || spec.templateId() == null || spec.command() == null) {
            return false;
        }
        List<String> command = spec.command();
        return switch (spec.templateId()) {
            case GET_SYSTEM_LOAD -> command.equals(List.of("uptime"));
            case GET_TOP_PROCESSES -> command.equals(List.of("ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"));
            case GET_OPEN_PORTS -> command.equals(List.of("ss", "-tulpn"));
            case CHECK_PORT_USAGE -> matchesPortUsage(command);
            case GET_SERVICE_STATUS -> matchesServiceStatus(command);
            case RESTART_SERVICE -> matchesRestartService(command);
            case VERIFY_SERVICE_ACTIVE -> matchesVerifyServiceActive(command);
        };
    }

    private void requireOnlyArgs(Map<String, Object> args, String... allowedKeys) {
        List<String> allowed = List.of(allowedKeys);
        List<String> unexpected = args.keySet().stream()
                .filter(key -> !allowed.contains(key))
                .toList();
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException("unexpected command template args: " + unexpected);
        }
    }

    private CommandSpec checked(CommandSpec spec) {
        if (!matches(spec)) {
            throw new IllegalStateException("built command does not match template: " + spec.templateId());
        }
        return spec;
    }

    private int requiredPort(Map<String, Object> args) {
        return NumericArgumentValidator.integerInRange(args.get("port"), 1, 65535)
                .orElseThrow(() -> new IllegalArgumentException("port must be an integer between 1 and 65535"));
    }

    private String requiredServiceName(Map<String, Object> args) {
        return ServiceNameValidator.extract(args.get("serviceName"))
                .orElseThrow(() -> new IllegalArgumentException("serviceName is invalid"));
    }

    private boolean matchesPortUsage(List<String> command) {
        if (command.size() != 5 || !command.subList(0, 4).equals(List.of("ss", "-tulpn", "sport", "="))) {
            return false;
        }
        String portArgument = command.get(4);
        if (!PORT_ARGUMENT_PATTERN.matcher(portArgument).matches()) {
            return false;
        }
        try {
            int port = Integer.parseInt(portArgument.substring(1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean matchesServiceStatus(List<String> command) {
        return command.size() == 5
                && command.subList(0, 4).equals(List.of("systemctl", "--no-pager", "status", "--"))
                && isSafeServiceName(command.get(4));
    }

    private boolean matchesRestartService(List<String> command) {
        return command.size() == 4
                && command.subList(0, 3).equals(List.of("systemctl", "restart", "--"))
                && isSafeServiceName(command.get(3));
    }

    private boolean matchesVerifyServiceActive(List<String> command) {
        return command.size() == 4
                && command.subList(0, 3).equals(List.of("systemctl", "is-active", "--"))
                && isSafeServiceName(command.get(3));
    }

    private boolean isSafeServiceName(String serviceName) {
        return ServiceNameValidator.validate(serviceName).isEmpty();
    }
}
