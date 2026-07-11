package com.cup.opsagent.auth;

import java.util.Set;

public record Actor(
        String actorId,
        ActorType actorType,
        Set<Permission> permissions,
        Set<Role> roles
) {
    public Actor {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        actorType = actorType == null ? ActorType.HUMAN : actorType;
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        Set<Permission> rolePermissions = roles.stream()
                .flatMap(role -> role.permissions().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Permission> directPermissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        permissions = java.util.stream.Stream.concat(directPermissions.stream(), rolePermissions.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Actor(String actorId, Set<Role> roles) {
        this(actorId, ActorType.HUMAN, Set.of(), roles);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
