package com.cup.opsagent.planner.llm.provider;

public class LlmProviderException extends RuntimeException {

    private final LlmProviderErrorCode code;

    public LlmProviderException(LlmProviderErrorCode code, String message) {
        super(LlmSafeLogSanitizer.sanitizeForException(message));
        this.code = code;
    }

    public LlmProviderException(LlmProviderErrorCode code, String message, Throwable cause) {
        super(LlmSafeLogSanitizer.sanitizeForException(message), cause);
        this.code = code;
    }

    public LlmProviderErrorCode code() {
        return code;
    }
}
