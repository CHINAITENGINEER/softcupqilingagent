package com.cup.opsagent.planner.llm;

public interface LlmPlannerClient {

    String createPlanJson(String userInput);

    default String createPlanJson(String userInput, String ragContext) {
        return createPlanJson(userInput);
    }
}
