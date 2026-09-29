package com.fitness.backend.common.config;

import com.fitness.backend.common.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 설정.
 *
 * <p>Bearer 인증을 등록한다 — 없으면 Authorize 버튼이 안 떠서 인증이 필요한
 * 엔드포인트를 UI에서 호출할 수 없다.
 *
 * <p>그리고 모든 응답에 설명을 채운다. 컨트롤러에 애노테이션이 없으면 springdoc이
 * 응답을 {@code Undocumented}로 표시해, 스웨거만 보고는 어떤 코드가 오는지도
 * 오류 본문이 어떻게 생겼는지도 알 수 없다. 엔드포인트 27개에 같은 애노테이션을
 * 반복해 다는 대신, 공통으로 붙는 것은 여기서 한 번에 채운다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    /** 코드별 기본 설명. 엔드포인트가 직접 단 설명이 있으면 그쪽이 우선이다. */
    private static final Map<String, String> DEFAULT_DESCRIPTIONS = Map.of(
            "200", "성공",
            "201", "생성됨",
            "204", "성공 — 본문 없음",
            "400", "요청 값이 올바르지 않음 (VALIDATION_ERROR)",
            "401", "인증이 필요하거나 토큰이 유효하지 않음 (AUTHENTICATION_REQUIRED)",
            "403", "권한 없음 (ACCESS_DENIED)",
            "404", "대상을 찾을 수 없음 (RESOURCE_NOT_FOUND)",
            "409", "현재 상태와 충돌 (DRAFT_SESSION_EXISTS 등)",
            "422", "처리할 수 없는 상태",
            "500", "서버 오류 (INTERNAL_ERROR)");

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("밸런스핏 API")
                        .version("v1")
                        .description("운동 습관 분석 기반 맞춤 루틴 추천 헬스 웹앱"))
                .components(new Components()
                        .addSecuritySchemes(BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * 설명이 빈 응답을 채우고, 모든 엔드포인트에 공통 오류 응답을 더한다.
     *
     * <p>오류 본문은 전부 {@code ErrorResponse} 한 모양이다(공통 에러 핸들러).
     * 그 사실이 스웨거에 드러나야 프론트가 오류 처리를 한 번만 짤 수 있다.
     */
    @Bean
    public OpenApiCustomizer fillResponseDescriptions() {
        return openApi -> {
            // 스키마 등록은 여기서 한다. OpenAPI 빈에 넣으면 springdoc이 스캔 결과로
            // components.schemas를 다시 만들면서 덮어써, $ref만 남고 정의가 사라진다.
            if (openApi.getComponents().getSchemas() == null
                    || !openApi.getComponents().getSchemas().containsKey(ERROR_SCHEMA)) {
                openApi.getComponents().addSchemas(ERROR_SCHEMA, ModelConverters.getInstance()
                        .readAllAsResolvedSchema(ErrorResponse.class).schema);
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(this::describe));
        };
    }

    private void describe(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }

        responses.forEach((code, response) -> {
            if (response.getDescription() == null || response.getDescription().isBlank()) {
                response.setDescription(DEFAULT_DESCRIPTIONS.getOrDefault(code, "응답"));
            }
        });

        // 어느 엔드포인트에서나 날 수 있는 것들. 이미 적혀 있으면 덮지 않는다.
        addErrorIfAbsent(responses, "401");
        addErrorIfAbsent(responses, "500");
    }

    private void addErrorIfAbsent(ApiResponses responses, String code) {
        if (responses.containsKey(code)) {
            return;
        }
        responses.addApiResponse(code, new ApiResponse()
                .description(DEFAULT_DESCRIPTIONS.get(code))
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)))));
    }
}
