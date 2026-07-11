package com.cup.opsagent.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class ActorWebMvcConfig implements WebMvcConfigurer {

    private final ActorMethodArgumentResolver actorMethodArgumentResolver;

    public ActorWebMvcConfig(ActorMethodArgumentResolver actorMethodArgumentResolver) {
        this.actorMethodArgumentResolver = actorMethodArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actorMethodArgumentResolver);
    }
}
