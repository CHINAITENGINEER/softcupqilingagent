package com.cup.opsagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class ActorMethodArgumentResolver implements HandlerMethodArgumentResolver {

    private final ActorResolver actorResolver;

    public ActorMethodArgumentResolver(ActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class) && Actor.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("HttpServletRequest is required to resolve current actor");
        }
        return actorResolver.resolveFromHeaders(
                request.getHeader(ActorResolver.ACTOR_ID_HEADER),
                request.getHeader(ActorResolver.ACTOR_TYPE_HEADER),
                request.getHeader(ActorResolver.ACTOR_ROLES_HEADER),
                request.getHeader(ActorResolver.ACTOR_PERMISSIONS_HEADER)
        );
    }
}
