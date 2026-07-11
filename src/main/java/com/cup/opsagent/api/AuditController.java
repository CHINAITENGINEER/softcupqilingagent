package com.cup.opsagent.api;

import com.cup.opsagent.audit.AuditIntegrityResult;
import com.cup.opsagent.audit.AuditIntegrityService;
import com.cup.opsagent.audit.AuditLogService;
import com.cup.opsagent.audit.AuditTrace;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogService auditLogService;
    private final AuditIntegrityService auditIntegrityService;

    public AuditController(AuditLogService auditLogService, AuditIntegrityService auditIntegrityService) {
        this.auditLogService = auditLogService;
        this.auditIntegrityService = auditIntegrityService;
    }

    @GetMapping("/traces")
    public Collection<AuditTrace> listTraces() {
        return auditLogService.listTraces();
    }

    @GetMapping("/traces/{traceId}/integrity")
    public AuditIntegrityResult checkTraceIntegrity(@PathVariable String traceId) {
        return auditIntegrityService.check(traceId);
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<AuditTrace> getTrace(@PathVariable String traceId) {
        return auditLogService.findTrace(traceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
