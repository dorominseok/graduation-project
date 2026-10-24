package com.fitness.backend.common.config;

import com.fitness.backend.common.web.ApiV1Controller;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 경로에 버전 접두사를 붙인다.
 *
 * <p>{@code server.servlet.context-path}를 쓰지 않은 이유: 그 방식은 actuator까지
 * 함께 밀려나 {@code /api/v1/actuator/health}가 된다. 헬스 체크는 {@code /actuator/health}에
 * 있어야 한다 — 배포와 모니터링이 그 경로를 본다.
 */
@Configuration
public class WebPathConfig implements WebMvcConfigurer {

    public static final String API_V1 = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1, c -> c.isAnnotationPresent(ApiV1Controller.class));
    }
}
