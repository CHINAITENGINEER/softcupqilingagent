package com.cup.opsagent.planner;

import com.cup.opsagent.planner.llm.FakeLlmPlannerClient;
import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.PlannerMode;
import com.cup.opsagent.planner.llm.provider.OpenAiCompatibleLlmPlannerClient;
import com.cup.opsagent.rag.AuditedRagAugmentor;
import com.cup.opsagent.rag.RagProperties;
import com.cup.opsagent.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PlannerConfiguration {

    @Bean
    @Primary
    public TaskPlanner taskPlanner(
            RuleBasedTaskPlanner ruleBasedTaskPlanner,
            FakeLlmPlannerClient fakeLlmPlannerClient,
            OpenAiCompatibleLlmPlannerClient openAiCompatibleLlmPlannerClient,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            AuditedRagAugmentor auditedRagAugmentor,
            @Value("${ops-agent.planner.mode:rule}") String plannerMode
    ) {
        PlannerMode mode = PlannerMode.from(plannerMode);
        return switch (mode) {
            case RULE -> ruleBasedTaskPlanner;
            case FAKE_LLM -> new LlmJsonTaskPlanner(fakeLlmPlannerClient, toolRegistry, objectMapper);
            case LLM -> new LlmJsonTaskPlanner(openAiCompatibleLlmPlannerClient, toolRegistry, objectMapper, ragProperties, auditedRagAugmentor);
        };
    }
}
