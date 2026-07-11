package com.cup.opsagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorMethodArgumentResolverTest {

    private final ActorMethodArgumentResolver resolver = new ActorMethodArgumentResolver(new ActorResolver());

    @Test
    void shouldSupportCurrentActorParameter() throws NoSuchMethodException {
        MethodParameter parameter = currentActorParameter();

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void shouldResolveActorFromRequestHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ActorResolver.ACTOR_ID_HEADER, "approver-1");
        request.addHeader(ActorResolver.ACTOR_TYPE_HEADER, "HUMAN");
        request.addHeader(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER,EXECUTOR");
        request.addHeader(ActorResolver.ACTOR_PERMISSIONS_HEADER, "");

        Actor actor = (Actor) resolver.resolveArgument(currentActorParameter(), null, new ServletWebRequest(request), null);

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
    void shouldResolveDirectPermissionsFromRequestHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ActorResolver.ACTOR_ID_HEADER, "custom-approver-1");
        request.addHeader(ActorResolver.ACTOR_TYPE_HEADER, "HUMAN");
        request.addHeader(ActorResolver.ACTOR_ROLES_HEADER, "OPERATOR");
        request.addHeader(ActorResolver.ACTOR_PERMISSIONS_HEADER, "APPROVE_MEDIUM_RISK_ACTION");

        Actor actor = (Actor) resolver.resolveArgument(currentActorParameter(), null, new ServletWebRequest(request), null);

        assertThat(actor.roles()).containsExactly(Role.OPERATOR);
        assertThat(actor.permissions()).containsExactly(Permission.APPROVE_MEDIUM_RISK_ACTION);
    }

    @Test
    void shouldRejectBlankActorHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ActorResolver.ACTOR_ID_HEADER, " ");
        request.addHeader(ActorResolver.ACTOR_ROLES_HEADER, "APPROVER");

        assertThatThrownBy(() -> resolver.resolveArgument(currentActorParameter(), null, new ServletWebRequest(request), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId must not be blank");
    }

    private MethodParameter currentActorParameter() throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("handle", Actor.class);
        return new MethodParameter(method, 0);
    }

    private static final class SampleController {
        @SuppressWarnings("unused")
        private void handle(@CurrentActor Actor actor) {
        }
    }
}
