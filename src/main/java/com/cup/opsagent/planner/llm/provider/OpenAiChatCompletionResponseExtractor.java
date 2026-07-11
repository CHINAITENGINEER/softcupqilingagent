package com.cup.opsagent.planner.llm.provider;

import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatCompletionResponse;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatMessage;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChoice;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatCompletionResponseExtractor {

    public String extractContent(OpenAiChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE, "LLM provider returned empty choices");
        }
        OpenAiChoice choice = response.choices().getFirst();
        OpenAiChatMessage message = choice == null ? null : choice.message();
        String content = message == null ? null : message.content();
        if (content == null || content.isBlank()) {
            throw new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE, "LLM provider returned empty message content");
        }
        if (content.length() > LlmJsonTaskPlanner.MAX_RAW_JSON_LENGTH) {
            throw new LlmProviderException(LlmProviderErrorCode.LLM_PROVIDER_RESPONSE_TOO_LARGE, "LLM provider returned too large message content");
        }
        return content;
    }
}
