package com.cup.opsagent.planner;

import com.cup.opsagent.agent.model.PlanStep;
import com.cup.opsagent.agent.model.TaskPlan;
import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedTaskPlanner implements TaskPlanner {

    private static final Pattern PORT_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,5})(?!\\d)");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("(?i)(nginx|sshd|ssh|mysql|postgresql|redis|docker|firewalld)");

    @Override
    public TaskPlan plan(String userInput) {
        String input = userInput == null ? "" : userInput.trim();
        String normalized = input.toLowerCase(Locale.ROOT);

        if (containsDangerousIntent(normalized)) {
            return new TaskPlan(
                    "DANGEROUS_INTENT",
                    "检测到危险或越权运维意图，禁止生成可执行工具调用",
                    RiskLevel.CRITICAL,
                    List.of()
            );
        }

        if (containsRestartIntent(normalized)) {
            return serviceName(input)
                    .map(serviceName -> singleStepPlan(
                            "SERVICE_RESTART",
                            "请求重启服务，需要人工确认",
                            RiskLevel.MEDIUM,
                            "restart-managed-service",
                            RestartServiceTool.NAME,
                            Map.of("serviceName", serviceName),
                            "重启服务属于中风险操作，必须进入审批流程"
                    ))
                    .orElseGet(() -> unknownPlan("无法识别需要重启的服务名"));
        }

        Optional<Integer> port = port(input);
        if (port.isPresent() && containsPortIntent(normalized)) {
            return singleStepPlan(
                    "PORT_DIAGNOSIS",
                    "检查端口占用情况",
                    RiskLevel.LOW,
                    "inspect-port-usage",
                    PortUsageTool.NAME,
                    Map.of("port", port.get()),
                    "用户询问特定端口是否被占用"
            );
        }

        if (containsOpenPortsIntent(normalized)) {
            return singleStepPlan(
                    "OPEN_PORTS_INSPECTION",
                    "列出当前监听端口",
                    RiskLevel.LOW,
                    "inspect-open-ports",
                    OpenPortsTool.NAME,
                    Map.of(),
                    "用户请求查看系统开放端口"
            );
        }

        if (containsHealthCheckIntent(normalized)) {
            List<PlanStep> steps = new ArrayList<>();
            steps.add(step("step-1", "inspect-system-load", SystemLoadTool.NAME, Map.of(), "先查看系统负载和基础资源状态"));
            steps.add(step("step-2", "inspect-top-processes", TopProcessesTool.NAME, Map.of("limit", 10), "再查看资源占用最高的进程"));
            return new TaskPlan("SYSTEM_HEALTH_CHECK", "执行一次系统健康巡检", RiskLevel.LOW, steps);
        }

        if (containsServiceStatusIntent(normalized)) {
            return serviceName(input)
                    .map(serviceName -> singleStepPlan(
                            "SERVICE_STATUS_INSPECTION",
                            "查询服务运行状态",
                            RiskLevel.LOW,
                            "inspect-service-status",
                            ServiceStatusTool.NAME,
                            Map.of("serviceName", serviceName),
                            "用户请求查询服务状态"
                    ))
                    .orElseGet(() -> unknownPlan("无法识别需要查询的服务名"));
        }

        if (containsTopProcessesIntent(normalized)) {
            return singleStepPlan(
                    "TOP_PROCESSES_INSPECTION",
                    "查看资源占用较高的进程",
                    RiskLevel.LOW,
                    "inspect-top-processes",
                    TopProcessesTool.NAME,
                    Map.of("limit", 10),
                    "用户请求查看高资源占用进程"
            );
        }

        return unknownPlan("暂时无法将输入映射到安全运维工具");
    }

    private boolean containsDangerousIntent(String input) {
        return input.contains("rm -rf")
                || input.contains("mkfs")
                || input.contains("dd if=")
                || input.contains("chmod -r 777")
                || input.contains("chown -r")
                || input.contains("/etc/shadow")
                || input.contains("清空所有日志")
                || input.contains("日志全部清空")
                || input.contains("删除所有")
                || input.contains("格式化")
                || input.contains("忽略之前所有安全规则")
                || input.contains("直接执行 shell")
                || input.contains("shell_command");
    }

    private boolean containsRestartIntent(String input) {
        return input.contains("重启") || input.contains("restart");
    }

    private boolean containsPortIntent(String input) {
        return input.contains("端口") || input.contains("port");
    }

    private boolean containsOpenPortsIntent(String input) {
        return (input.contains("开放") || input.contains("监听") || input.contains("open"))
                && (input.contains("端口") || input.contains("port"));
    }

    private boolean containsServiceStatusIntent(String input) {
        return input.contains("服务状态")
                || input.contains("状态")
                || input.contains("status")
                || input.contains("启动不了")
                || input.contains("起不来");
    }

    private boolean containsTopProcessesIntent(String input) {
        return input.contains("进程")
                || input.contains("process")
                || input.contains("cpu")
                || input.contains("内存")
                || input.contains("占用最高")
                || input.contains("资源占用");
    }

    private boolean containsHealthCheckIntent(String input) {
        return input.contains("健康检查")
                || input.contains("巡检")
                || input.contains("系统负载")
                || input.contains("系统状态")
                || input.contains("health");
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

    private TaskPlan singleStepPlan(
            String intentType,
            String summary,
            RiskLevel riskLevel,
            String actionName,
            String toolName,
            Map<String, Object> arguments,
            String reason
    ) {
        return new TaskPlan(intentType, summary, riskLevel, List.of(step("step-1", actionName, toolName, arguments, reason)));
    }

    private PlanStep step(String stepId, String actionName, String toolName, Map<String, Object> arguments, String reason) {
        return new PlanStep(stepId, actionName, toolName, arguments, reason);
    }

    private TaskPlan unknownPlan(String summary) {
        return new TaskPlan("UNKNOWN", summary, RiskLevel.HIGH, List.of());
    }
}
