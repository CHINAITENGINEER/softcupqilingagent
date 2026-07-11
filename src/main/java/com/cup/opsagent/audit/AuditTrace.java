package com.cup.opsagent.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AuditTrace {

    private final String traceId;
    private final String userInput;
    private final Instant startedAt;
    private Instant endedAt;
    private String status;
    private String finalAnswer;
    private final List<AuditEvent> events = new ArrayList<>();

    public AuditTrace(String traceId, String userInput) {
        this(traceId, userInput, Instant.now(), null, "RUNNING", null, List.of());
    }

    AuditTrace(String traceId, String userInput, Instant startedAt, Instant endedAt, String status, String finalAnswer, List<AuditEvent> events) {
        this.traceId = traceId;
        this.userInput = userInput;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.events.addAll(events == null ? List.of() : events);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getUserInput() {
        return userInput;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public List<AuditEvent> getEvents() {
        return List.copyOf(events);
    }

    public void addEvent(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        String previousHash = events.isEmpty() ? AuditHasher.GENESIS_HASH : events.getLast().eventHash();
        String eventHash = AuditHasher.hashEvent(event, previousHash);
        events.add(event.withHashes(previousHash, eventHash));
    }

    public boolean hasValidHashChain() {
        String previousHash = AuditHasher.GENESIS_HASH;
        for (AuditEvent event : events) {
            if (!previousHash.equals(event.previousHash())) {
                return false;
            }
            if (!AuditHasher.hashEvent(event, previousHash).equals(event.eventHash())) {
                return false;
            }
            previousHash = event.eventHash();
        }
        return true;
    }

    public void finish(String status, String finalAnswer) {
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.endedAt = Instant.now();
    }
}
