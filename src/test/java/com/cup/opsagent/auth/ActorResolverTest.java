package com.cup.opsagent.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorResolverTest {

    private final ActorResolver actorResolver = new ActorResolver();

    @Test
    void shouldResolveActorFromHeadersWithMultipleRoles() {
        Actor actor = actorResolver.resolveFromHeaders("approver-1", "HUMAN", "APPROVER, EXECUTOR", "");

        assertThat(actor.actorId()).isEqualTo("approver-1");
        assertThat(actor.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(actor.roles()).containsExactlyInAnyOrder(Role.APPROVER, Role.EXECUTOR);
        assertThat(actor.permissions()).containsExactlyInAnyOrder(
                Permission.APPROVE_MEDIUM_RISK_ACTION,
                Permission.REJECT_MEDIUM_RISK_ACTION,
                Permission.EXECUTE_APPROVED_ACTION
        );
    }

    @Test
    void shouldResolveRolesCaseInsensitively() {
        Actor actor = actorResolver.resolveFromHeaders("admin-1", "human", "admin", "");

        assertThat(actor.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(actor.roles()).containsExactly(Role.ADMIN);
    }

    @Test
    void shouldResolveDirectPermissionsWithoutRoleCoupling() {
        Actor actor = actorResolver.resolveFromHeaders(
                "custom-approver-1",
                "HUMAN",
                "OPERATOR",
                "APPROVE_MEDIUM_RISK_ACTION"
        );

        assertThat(actor.roles()).containsExactly(Role.OPERATOR);
        assertThat(actor.permissions()).containsExactly(Permission.APPROVE_MEDIUM_RISK_ACTION);
    }

    @Test
    void shouldDefaultToHumanOperatorWhenOptionalHeadersAreBlank() {
        Actor actor = actorResolver.resolveFromHeaders("operator-1", " ", " ", " ");

        assertThat(actor.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(actor.roles()).containsExactly(Role.OPERATOR);
        assertThat(actor.permissions()).isEmpty();
    }

    @Test
    void shouldRejectBlankActorId() {
        assertThatThrownBy(() -> actorResolver.resolveFromHeaders(" ", "HUMAN", "APPROVER", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId must not be blank");
    }
}
