package com.cup.opsagent.auth;

import java.util.Set;

public enum Role {
    OPERATOR(Set.of()),
    APPROVER(Set.of(Permission.APPROVE_MEDIUM_RISK_ACTION, Permission.REJECT_MEDIUM_RISK_ACTION)),
    EXECUTOR(Set.of(Permission.EXECUTE_APPROVED_ACTION)),
    ADMIN(Set.of(Permission.APPROVE_MEDIUM_RISK_ACTION, Permission.REJECT_MEDIUM_RISK_ACTION, Permission.EXECUTE_APPROVED_ACTION));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
