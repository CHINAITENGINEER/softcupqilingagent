package com.cup.opsagent.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JdbcAuditTraceRepository implements AuditTraceRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditTraceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AuditTrace auditTrace) {
        if (exists(auditTrace.getTraceId())) {
            jdbcTemplate.update("""
                            update audit_traces
                            set user_input = ?, started_at = ?, ended_at = ?, status = ?, final_answer = ?
                            where trace_id = ?
                            """,
                    auditTrace.getUserInput(),
                    timestamp(auditTrace.getStartedAt()),
                    timestamp(auditTrace.getEndedAt()),
                    auditTrace.getStatus(),
                    auditTrace.getFinalAnswer(),
                    auditTrace.getTraceId()
            );
        } else {
            jdbcTemplate.update("""
                            insert into audit_traces (trace_id, user_input, started_at, ended_at, status, final_answer)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    auditTrace.getTraceId(),
                    auditTrace.getUserInput(),
                    timestamp(auditTrace.getStartedAt()),
                    timestamp(auditTrace.getEndedAt()),
                    auditTrace.getStatus(),
                    auditTrace.getFinalAnswer()
            );
        }
        jdbcTemplate.update("delete from audit_events where trace_id = ?", auditTrace.getTraceId());
        for (AuditEvent event : auditTrace.getEvents()) {
            jdbcTemplate.update("""
                            insert into audit_events (trace_id, event_type, event_time, success, payload, error_message, previous_hash, event_hash)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    event.traceId(),
                    event.eventType().name(),
                    timestamp(event.eventTime()),
                    event.success(),
                    toJson(event.payload()),
                    event.errorMessage(),
                    event.previousHash(),
                    event.eventHash()
            );
        }
    }

    @Override
    public Optional<AuditTrace> findByTraceId(String traceId) {
        return jdbcTemplate.query("""
                        select trace_id, user_input, started_at, ended_at, status, final_answer
                        from audit_traces
                        where trace_id = ?
                        """,
                (resultSet, rowNum) -> toTrace(
                        resultSet.getString("trace_id"),
                        resultSet.getString("user_input"),
                        instant(resultSet.getTimestamp("started_at")),
                        instant(resultSet.getTimestamp("ended_at")),
                        resultSet.getString("status"),
                        resultSet.getString("final_answer")
                ),
                traceId
        ).stream().findFirst();
    }

    @Override
    public Collection<AuditTrace> findAll() {
        return jdbcTemplate.query("""
                        select trace_id, user_input, started_at, ended_at, status, final_answer
                        from audit_traces
                        """,
                (resultSet, rowNum) -> toTrace(
                        resultSet.getString("trace_id"),
                        resultSet.getString("user_input"),
                        instant(resultSet.getTimestamp("started_at")),
                        instant(resultSet.getTimestamp("ended_at")),
                        resultSet.getString("status"),
                        resultSet.getString("final_answer")
                )
        );
    }

    private AuditTrace toTrace(String traceId, String userInput, Instant startedAt, Instant endedAt, String status, String finalAnswer) {
        List<AuditEvent> events = jdbcTemplate.query("""
                        select trace_id, event_type, event_time, success, payload, error_message, previous_hash, event_hash
                        from audit_events
                        where trace_id = ?
                        order by event_time asc, event_id asc
                        """,
                (resultSet, rowNum) -> new AuditEvent(
                        resultSet.getString("trace_id"),
                        AuditEventType.valueOf(resultSet.getString("event_type")),
                        instant(resultSet.getTimestamp("event_time")),
                        resultSet.getBoolean("success"),
                        fromJson(resultSet.getString("payload")),
                        resultSet.getString("error_message"),
                        resultSet.getString("previous_hash"),
                        resultSet.getString("event_hash")
                ),
                traceId
        );
        return new AuditTrace(traceId, userInput, startedAt, endedAt, status, finalAnswer, events);
    }

    private boolean exists(String traceId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from audit_traces where trace_id = ?", Integer.class, traceId);
        return count != null && count > 0;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to serialize audit payload", exception);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to deserialize audit payload", exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
