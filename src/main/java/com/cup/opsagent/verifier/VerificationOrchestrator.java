package com.cup.opsagent.verifier;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class VerificationOrchestrator {

    private final List<ExecutionVerifier> verifiers;

    public VerificationOrchestrator(List<ExecutionVerifier> verifiers) {
        this.verifiers = verifiers.stream()
                .sorted(Comparator.comparingInt(verifier -> {
                    Order order = verifier.getClass().getAnnotation(Order.class);
                    return order == null ? Integer.MAX_VALUE : order.value();
                }))
                .toList();
    }

    public VerificationResult verify(VerificationContext context) {
        return verifiers.stream()
                .filter(verifier -> verifier.supports(context.toolCall(), context.toolDefinition()))
                .findFirst()
                .map(verifier -> verifier.verify(context))
                .orElseGet(() -> VerificationResult.failed(
                        "NoMatchingVerifier",
                        "no verifier supports tool: " + context.toolCall().toolName(),
                        Map.of("toolName", context.toolCall().toolName()),
                        List.of("为该工具实现专用 ExecutionVerifier")
                ));
    }
}
