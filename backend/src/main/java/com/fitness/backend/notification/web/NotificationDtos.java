package com.fitness.backend.notification.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** 알림 설정·구독(「알림_설계서」, API 명세 12장) */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record SettingsResponse(
            @Schema(description = "서버에 VAPID 키가 있어 알림을 보낼 수 있는지. false면 화면은 알림 켜기를 막는다")
            boolean pushAvailable,
            @Schema(description = "브라우저가 구독할 때 쓰는 공개키(base64url). pushAvailable이 false면 null")
            String vapidPublicKey,
            @Schema(description = "이 계정에 등록된 기기(구독) 수") long subscriptionCount,
            @Schema(description = "저장된 설정인지. false면 기본값이고 리마인더 요일은 최근 기록에서 고른 추천이다")
            boolean saved,
            Reminder reminder,
            Summary summary,
            @Schema(description = "응원 받음") boolean cheer,
            @Schema(description = "그룹 소식(새 멤버 — 방장에게, 방장 위임 — 새 방장에게)") boolean group) {
    }

    public record Reminder(
            boolean enabled,
            @NotNull @Schema(description = "운동하는 요일. 이 요일에만 보낸다") List<@NotNull DayOfWeek> days,
            @NotNull @JsonFormat(pattern = "HH:mm")
            @Schema(description = "08:00~21:30, 30분 단위", example = "19:00") LocalTime time) {
    }

    public record Summary(
            boolean enabled,
            @NotNull DayOfWeek day,
            @NotNull @JsonFormat(pattern = "HH:mm")
            @Schema(description = "08:00~21:30, 30분 단위", example = "20:00") LocalTime time) {
    }

    public record UpdateRequest(
            @NotNull @Valid Reminder reminder,
            @NotNull @Valid Summary summary,
            boolean cheer,
            boolean group) {
    }

    /** 브라우저 {@code PushSubscription.toJSON()}의 모양 그대로 */
    public record SubscribeRequest(
            @NotBlank @Size(max = 1000) String endpoint,
            @NotNull @Valid Keys keys) {
    }

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }
}
