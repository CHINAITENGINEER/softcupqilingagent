package com.cup.opsagent.safety.policy;

import com.cup.opsagent.safety.Policy;
import com.cup.opsagent.safety.PolicyContext;
import com.cup.opsagent.safety.PolicyDecision;
import com.cup.opsagent.safety.PolicyHit;
import com.cup.opsagent.tool.core.RiskLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Order(20)
public class DangerousIntentPolicy implements Policy {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("rm\\s+-(?=[a-z]*r)(?=[a-z]*f)[a-z]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+-r\\s+-f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+-f\\s+-r", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mkfs", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dd\\s+if=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chmod\\s+-r\\s+777", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chown\\s+-r", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/etc/(shadow|sudoers|passwd)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("truncate\\s+-s\\s+0\\s+(/var/log|/etc|/boot|/usr|/bin|/sbin)", Pattern.CASE_INSENSITIVE),
            Pattern.compile(":\\s*>\\s*/var/log", Pattern.CASE_INSENSITIVE),
            Pattern.compile("curl.+\\|\\s*(sh|bash)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget.+\\|\\s*(sh|bash)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(ignore|bypass|disable).+(safety|policy|audit|approval)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shell_command", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "DangerousIntentPolicy";
    }

    @Override
    public Optional<PolicyDecision> evaluate(PolicyContext context) {
        String combinedText = (context.userInput() + " " + argumentsText(context.toolCall() == null ? null : context.toolCall().arguments()))
                .toLowerCase(Locale.ROOT);
        if (containsDangerousChineseIntent(combinedText) || matchesDangerousPattern(combinedText)) {
            String reason = "Dangerous intent detected";
            return Optional.of(PolicyDecision.block(
                    RiskLevel.CRITICAL,
                    reason,
                    List.of(new PolicyHit(name(), "CRITICAL", reason)),
                    List.of("拒绝破坏性或越权请求", "请改用只读诊断工具或提交人工审批")
            ));
        }
        return Optional.empty();
    }

    private boolean matchesDangerousPattern(String text) {
        return DANGEROUS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    private boolean containsDangerousChineseIntent(String text) {
        return text.contains("清空所有日志")
                || text.contains("日志全部清空")
                || text.contains("删除所有")
                || text.contains("格式化")
                || text.contains("忽略之前所有安全规则")
                || text.contains("直接执行 shell")
                || text.contains("绕过安全")
                || text.contains("禁用审计")
                || text.contains("关闭审计")
                || text.contains("读取 shadow");
    }

    private String argumentsText(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        return arguments.toString();
    }
}
