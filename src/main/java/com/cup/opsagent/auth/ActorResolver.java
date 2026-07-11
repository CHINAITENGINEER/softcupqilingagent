package com.cup.opsagent.auth;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ActorResolver {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    public static final String ACTOR_TYPE_HEADER = "X-Actor-Type";
    public static final String ACTOR_ROLES_HEADER = "X-Actor-Roles";
    public static final String ACTOR_PERMISSIONS_HEADER = "X-Actor-Permissions";

    public Actor resolve(String actorId, Set<String> roleNames) {
        return resolve(actorId, null, roleNames, Set.of());
    }

    public Actor resolve(String actorId, String actorTypeName, Set<String> roleNames, Set<String> permissionNames) {
        Set<Role> roles = roleNames == null || roleNames.isEmpty()
                ? Set.of(Role.OPERATOR)
                : roleNames.stream().map(this::parseRole).collect(Collectors.toUnmodifiableSet());
        Set<Permission> permissions = permissionNames == null || permissionNames.isEmpty()
                ? Set.of()
                : permissionNames.stream().map(this::parsePermission).collect(Collectors.toUnmodifiableSet());
        return new Actor(actorId, parseActorType(actorTypeName), permissions, roles);
    }

    public Actor resolveFromHeaders(String actorIdHeader, String rolesHeader) {
        return resolveFromHeaders(actorIdHeader, null, rolesHeader, null);
    }

    public Actor resolveFromHeaders(String actorIdHeader, String actorTypeHeader, String rolesHeader, String permissionsHeader) {
        return resolve(actorIdHeader, actorTypeHeader, parseCsv(rolesHeader), parseCsv(permissionsHeader));
    }

    private Set<String> parseCsv(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private ActorType parseActorType(String actorTypeName) {
        if (actorTypeName == null || actorTypeName.isBlank()) {
            return ActorType.HUMAN;
        }
        return ActorType.valueOf(actorTypeName.trim().toUpperCase());
    }

    private Role parseRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Role.OPERATOR;
        }
        return Role.valueOf(roleName.trim().toUpperCase());
    }

    private Permission parsePermission(String permissionName) {
        return Permission.valueOf(permissionName.trim().toUpperCase());
    }
}
