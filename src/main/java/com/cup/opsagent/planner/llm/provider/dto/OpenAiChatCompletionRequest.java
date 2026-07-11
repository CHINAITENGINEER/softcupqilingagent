package com.cup.opsagent.planner.llm.provider.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatCompletionRequest(
        String model,
        List<OpenAiChatMessage> messages,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("response_format") OpenAiResponseFormat responseFormat
) {
}
