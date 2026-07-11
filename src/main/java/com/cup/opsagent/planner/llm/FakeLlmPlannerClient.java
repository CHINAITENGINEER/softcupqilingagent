package com.cup.opsagent.planner.llm;

import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FakeLlmPlannerClient implements LlmPlannerClient {

    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?i)(nginx|sshd|ssh|mysql|postgresql|redis|docker|firewalld)");
    private static final Pattern PORT_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,5})(?!\\d)");

    private final ObjectMapper objectMapper;

    public FakeLlmPlannerClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String createPlanJson(String userInput) {
        String input = userInput == null ? "" : userInput;
        String normalized = input.toLowerCase(Locale.ROOT);
        LlmPlanResponse response;
        if (containsDangerousIntent(normalized)) {
            response = new LlmPlanResponse(
                    "DANGEROUS_INTENT",
                    "用户请求包含危险或越权运维意图，未生成工具步骤",
                    "CRITICAL",
                    List.of()
            );
        } else if (containsRestartIntent(normalized)) {
            response = serviceName(input)
                    .map(serviceName -> new LlmPlanResponse(
                            "SERVICE_RESTART",
                            "用户希望重启服务并进入受控执行链路",
                            "MEDIUM",
                            List.of(new LlmPlanStepResponse(
                                    "step-1",
                                    "restart-managed-service",
                                    RestartServiceTool.NAME,
                                    Map.of("serviceName", serviceName),
                                    "用户请求重启指定服务"
                            ))
                    ))
                    .orElseGet(() -> unknown("无法识别需要重启的服务名"));
        } else if (containsHealthCheckIntent(normalized)) {
            response = new LlmPlanResponse(
                    "SYSTEM_HEALTH_CHECK",
                    "用户希望执行系统健康巡检",
                    "LOW",
                    List.of(
                            new LlmPlanStepResponse("step-1", "inspect-system-load", SystemLoadTool.NAME, Map.of(), "查看系统负载"),
                            new LlmPlanStepResponse("step-2", "inspect-top-processes", TopProcessesTool.NAME, Map.of("limit", 10), "查看资源占用最高的进程")
                    )
            );
        } else if (containsServiceStatusIntent(normalized)) {
            response = serviceName(input)
                    .map(serviceName -> new LlmPlanResponse(
                            "SERVICE_STATUS_INSPECTION",
                            "用户希望查询服务状态",
                            "LOW",
                            List.of(new LlmPlanStepResponse(
                                    "step-1",
                                    "inspect-service-status",
                                    ServiceStatusTool.NAME,
                                    Map.of("serviceName", serviceName),
                                    "查询指定服务状态"
                            ))
                    ))
                    .orElseGet(() -> unknown("无法识别需要查询的服务名"));
        } else if (containsPortIntent(normalized)) {
            response = port(input)
                    .map(port -> new LlmPlanResponse(
                            "PORT_DIAGNOSIS",
                            "用户希望检查端口占用情况",
                            "LOW",
                            List.of(new LlmPlanStepResponse(
                                    "step-1",
                                    "inspect-port-usage",
                                    PortUsageTool.NAME,
                                    Map.of("port", port),
                                    "查询指定端口是否被占用"
                            ))
                    ))
                    .orElseGet(() -> new LlmPlanResponse(
                            "OPEN_PORTS_INSPECTION",
                            "用户希望查看当前监听端口",
                            "LOW",
                            List.of(new LlmPlanStepResponse("step-1", "inspect-open-ports", OpenPortsTool.NAME, Map.of(), "列出当前开放端口"))
                    ));
        } else {
            response = unknown("暂时无法将输入映射到安全运维工具");
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize fake LLM plan", exception);
        }
    }

    private LlmPlanResponse unknown(String summary) {
        return new LlmPlanResponse("UNKNOWN", summary, "HIGH", List.of());
    }

    private boolean containsDangerousIntent(String input) {
        return input.contains("rm -rf")
                || input.contains("mkfs")
                || input.contains("dd if=")
                || input.contains("/etc/shadow")
                || input.contains("忽略之前所有安全规则")
                || input.contains("直接执行 shell")
                || input.contains("shell_command");
    }

    private boolean containsRestartIntent(String input) {
        return input.contains("重启") || input.contains("restart");
    }

    private boolean containsHealthCheckIntent(String input) {
        return input.contains("健康检查") || input.contains("巡检") || input.contains("health");
    }

    private boolean containsServiceStatusIntent(String input) {
        return input.contains("服务状态") || input.contains("status") || input.contains("起不来");
    }

    private boolean containsPortIntent(String input) {
        return input.contains("端口") || input.contains("port");
    }

    private Optional<String> serviceName(String input) {
        Matcher matcher = SERVICE_PATTERN.matcher(input);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String service = matcher.group(1).toLowerCase(Locale.ROOT);
        if (service.equals("ssh")) {
            return Optional.of("sshd");
        }
        return Optional.of(service);
    }

    private Optional<Integer> port(String input) {
        Matcher matcher = PORT_PATTERN.matcher(input);
        while (matcher.find()) {
            int port = Integer.parseInt(matcher.group(1));
            if (port >= 1 && port <= 65535) {
                return Optional.of(port);
            }
        }
        return Optional.empty();
    }
}
