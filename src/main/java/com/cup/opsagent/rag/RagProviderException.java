package com.cup.opsagent.rag;

import com.cup.opsagent.planner.llm.provider.LlmSafeLogSanitizer;

public class RagProviderException extends RuntimeException {

    private final RagProviderErrorCode code;

    public RagProviderException(RagProviderErrorCode code, String message) {
        super(LlmSafeLogSanitizer.sanitizeForException(message));
        this.code = code;
    }

    public RagProviderException(RagProviderErrorCode code, String message, Throwable cause) {
        super(LlmSafeLogSanitizer.sanitizeForException(message), cause);
        this.code = code;
    }

    public RagProviderErrorCode code() {
        return code;
    }
}
