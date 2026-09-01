package com.fitness.backend.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1} 아래에 놓이는 REST 컨트롤러임을 표시한다.
 *
 * <p>경로 접두사는 각 컨트롤러의 {@code @RequestMapping}에 적지 않는다. 27개
 * 엔드포인트에 같은 문자열을 반복하면 하나만 빠뜨려도 그 엔드포인트가 조용히
 * 다른 경로에 붙기 때문이다. 대신 {@link WebPathConfig}가 이 애노테이션이 달린
 * 컨트롤러 전체에 접두사를 얹는다.
 *
 * <p>{@code /actuator/**}는 스프링이 별도로 매핑하므로 영향을 받지 않는다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@RestController
public @interface ApiV1Controller {
}
