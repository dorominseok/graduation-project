package com.fitness.backend.notification.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.notification.service.NotificationSettingsService;
import com.fitness.backend.notification.web.NotificationDtos.SettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 알림 설정과 기기 등록(「알림_설계서」, API 명세 12장) */
@ApiV1Controller
@RequestMapping("/users/me")
public class NotificationController {

    private final NotificationSettingsService settingsService;

    public NotificationController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Operation(summary = "알림 설정 조회",
            description = "저장한 적이 없으면 기본값(리마인더 요일은 최근 기록에서 고른 추천)과 saved=false")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "설정과 서버의 공개키")})
    @GetMapping("/notification-settings")
    public SettingsResponse settings(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal) {
        return settingsService.get(principal.userId());
    }

    @Operation(summary = "알림 설정 저장")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장한 설정"),
            @ApiResponse(responseCode = "400", description = "시각이 08:00~21:30·30분 단위가 아니거나, 리마인더를 켜고 요일이 없음")
    })
    @PutMapping("/notification-settings")
    public SettingsResponse update(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                   @Valid @RequestBody NotificationDtos.UpdateRequest request) {
        return settingsService.update(principal.userId(), request);
    }

    @Operation(summary = "기기 등록(푸시 구독)",
            description = "브라우저 PushSubscription.toJSON() 그대로. 같은 푸시 주소면 주인만 바꾼다. 처음이면 설정을 기본값으로 만든다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 뒤의 설정"),
            @ApiResponse(responseCode = "400", description = "푸시 주소가 https가 아니거나 키가 올바르지 않음")
    })
    @PostMapping("/push-subscriptions")
    public SettingsResponse subscribe(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                      @Valid @RequestBody NotificationDtos.SubscribeRequest request) {
        return settingsService.subscribe(principal.userId(), request.endpoint(), request.keys().p256dh(),
                request.keys().auth());
    }

    @Operation(summary = "기기 등록 해제", description = "내 기기가 아니거나 없으면 아무 일도 하지 않는다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "해제")})
    @DeleteMapping("/push-subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
                                            @Valid @RequestBody NotificationDtos.UnsubscribeRequest request) {
        settingsService.unsubscribe(principal.userId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
