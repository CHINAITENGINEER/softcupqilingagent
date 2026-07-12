package com.cup.opsagent.approval;

import com.cup.opsagent.tool.core.RiskLevel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JdbcApprovalRepository implements ApprovalRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcApprovalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveApproval(ApprovalRecord approvalRecord) {
        if (exists("approval_records", "approval_id", approvalRecord.approvalId())) {
            jdbcTemplate.update("""
                            update approval_records
                            set trace_id = ?, step_id = ?, requester_id = ?, tool_name = ?, canonical_arguments = ?, risk_level = ?, action_hash = ?,
                                status = ?, reason = ?, created_at = ?, expires_at = ?, decided_at = ?, decided_by = ?
                            where approval_id = ?
                            """,
                    approvalRecord.traceId(),
                    approvalRecord.stepId(),
                    approvalRecord.requesterId(),
                    approvalRecord.toolName(),
                    toJson(approvalRecord.canonicalArguments()),
                    approvalRecord.riskLevel().name(),
                    approvalRecord.actionHash(),
                    approvalRecord.status().name(),
                    approvalRecord.reason(),
                    timestamp(approvalRecord.createdAt()),
                    timestamp(approvalRecord.expiresAt()),
                    timestamp(approvalRecord.decidedAt()),
                    approvalRecord.decidedBy(),
                    approvalRecord.approvalId()
            );
            return;
        }
        jdbcTemplate.update("""
                        insert into approval_records (
                            approval_id, trace_id, step_id, requester_id, tool_name, canonical_arguments, risk_level, action_hash,
                            status, reason, created_at, expires_at, decided_at, decided_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                approvalRecord.approvalId(),
                approvalRecord.traceId(),
                approvalRecord.stepId(),
                approvalRecord.requesterId(),
                approvalRecord.toolName(),
                toJson(approvalRecord.canonicalArguments()),
                approvalRecord.riskLevel().name(),
                approvalRecord.actionHash(),
                approvalRecord.status().name(),
                approvalRecord.reason(),
                timestamp(approvalRecord.createdAt()),
                timestamp(approvalRecord.expiresAt()),
                timestamp(approvalRecord.decidedAt()),
                approvalRecord.decidedBy()
        );
    }

    @Override
    public Optional<ApprovalRecord> findApproval(String approvalId) {
        return jdbcTemplate.query("""
                        select approval_id, trace_id, step_id, requester_id, tool_name, canonical_arguments, risk_level, action_hash,
                               status, reason, created_at, expires_at, decided_at, decided_by
                        from approval_records
                        where approval_id = ?
                        """,
                approvalRowMapper(),
                approvalId
        ).stream().findFirst();
    }

    @Override
    public List<ApprovalRecord> listApprovals() {
        return jdbcTemplate.query("""
                        select approval_id, trace_id, step_id, requester_id, tool_name, canonical_arguments, risk_level, action_hash,
                               status, reason, created_at, expires_at, decided_at, decided_by
                        from approval_records
                        order by created_at desc, approval_id asc
                        """,
                approvalRowMapper()
        );
    }

    @Override
    public void saveLease(ExecutionLease executionLease) {
        if (exists("execution_leases", "lease_id", executionLease.leaseId())) {
            jdbcTemplate.update("""
                            update execution_leases
                            set approval_id = ?, action_hash = ?, tool_name = ?, canonical_arguments = ?, issued_at = ?, expires_at = ?, consumed_at = ?
                            where lease_id = ?
                            """,
                    executionLease.approvalId(),
                    executionLease.actionHash(),
                    executionLease.toolName(),
                    toJson(executionLease.canonicalArguments()),
                    timestamp(executionLease.issuedAt()),
                    timestamp(executionLease.expiresAt()),
                    timestamp(executionLease.consumedAt()),
                    executionLease.leaseId()
            );
            return;
        }
        jdbcTemplate.update("""
                        insert into execution_leases (
                            lease_id, approval_id, action_hash, tool_name, canonical_arguments, issued_at, expires_at, consumed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                executionLease.leaseId(),
                executionLease.approvalId(),
                executionLease.actionHash(),
                executionLease.toolName(),
                toJson(executionLease.canonicalArguments()),
                timestamp(executionLease.issuedAt()),
                timestamp(executionLease.expiresAt()),
                timestamp(executionLease.consumedAt())
        );
    }

    @Override
    public Optional<ExecutionLease> findLease(String leaseId) {
        return jdbcTemplate.query("""
                        select lease_id, approval_id, action_hash, tool_name, canonical_arguments, issued_at, expires_at, consumed_at
                        from execution_leases
                        where lease_id = ?
                        """,
                leaseRowMapper(),
                leaseId
        ).stream().findFirst();
    }

    private boolean exists(String tableName, String idColumn, String id) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + idColumn + " = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private RowMapper<ApprovalRecord> approvalRowMapper() {
        return (resultSet, rowNum) -> new ApprovalRecord(
                resultSet.getString("approval_id"),
                resultSet.getString("trace_id"),
                resultSet.getString("step_id"),
                resultSet.getString("requester_id"),
                resultSet.getString("tool_name"),
                fromJson(resultSet.getString("canonical_arguments")),
                RiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getString("action_hash"),
                ApprovalStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason"),
                instant(resultSet.getTimestamp("created_at")),
                instant(resultSet.getTimestamp("expires_at")),
                instant(resultSet.getTimestamp("decided_at")),
                resultSet.getString("decided_by")
        );
    }

    private RowMapper<ExecutionLease> leaseRowMapper() {
        return (resultSet, rowNum) -> new ExecutionLease(
                resultSet.getString("lease_id"),
                resultSet.getString("approval_id"),
                resultSet.getString("action_hash"),
                resultSet.getString("tool_name"),
                fromJson(resultSet.getString("canonical_arguments")),
                instant(resultSet.getTimestamp("issued_at")),
                instant(resultSet.getTimestamp("expires_at")),
                instant(resultSet.getTimestamp("consumed_at"))
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to serialize arguments", exception);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to deserialize arguments", exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
