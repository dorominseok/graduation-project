package com.fitness.backend.exercise.domain;

/**
 * 내 종목의 부위 — 사용자가 고른 탐색 분류(와 하체·목이면 앞·뒤)를 저장 값으로 바꾼다(「내종목_설계서」 4장).
 *
 * <p>사용자는 부위 고르기 화면과 같은 14개 이름으로 고른다. 저장은 기본 종목과 같은 칸
 * ({@code primary_muscle}·{@code delt_region}·{@code body_part}·{@code push_pull})이라, 분석·회복·탐색 분류가
 * 기본 종목과 똑같이 읽는다. {@code push_pull}은 분석에 쓰이지 않고 종목 상세의 "동작" 줄에만 보여,
 * 기본 종목에서 그 근육에 가장 많은 값을 쓴다.
 */
public record CustomTarget(String primaryMuscle, DeltRegion deltRegion, BodyPart bodyPart, PushPull pushPull) {

    /**
     * 분석은 하체를 앞허벅지와 뒤허벅지·둔근으로, 목을 앞·뒤로 나눠 본다. 칸 하나로는 어느 쪽인지 몰라 한 번 더 묻는다.
     * 처음엔 하체만이라 이름이 {@code LegSide}였다(2026-11-27 목을 더하며 바꿈).
     */
    public enum Side {
        FRONT, BACK
    }

    /** 앞·뒤를 골라야 하는 분류인지 */
    public static boolean needsSide(BrowseCategory category) {
        return category == BrowseCategory.LEGS || category == BrowseCategory.NECK;
    }

    /**
     * @param side {@code category}가 하체·목일 때만 쓴다. 그런데 없으면 {@link IllegalArgumentException}
     */
    public static CustomTarget of(BrowseCategory category, Side side) {
        return switch (category) {
            case CHEST -> new CustomTarget("chest", null, BodyPart.CHEST, PushPull.PUSH);
            case BACK -> new CustomTarget("lats", null, BodyPart.BACK, PushPull.PULL);
            case SHOULDERS_FRONT -> new CustomTarget("shoulders", DeltRegion.FRONT, BodyPart.SHOULDERS, PushPull.PUSH);
            case SHOULDERS_REAR -> new CustomTarget("shoulders", DeltRegion.REAR, BodyPart.SHOULDERS, PushPull.PULL);
            case TRAPS -> new CustomTarget("traps", null, BodyPart.BACK, PushPull.PULL);
            case TRICEPS -> new CustomTarget("triceps", null, BodyPart.ARMS, PushPull.PUSH);
            case BICEPS -> new CustomTarget("biceps", null, BodyPart.ARMS, PushPull.PULL);
            case FOREARMS -> new CustomTarget("forearms", null, BodyPart.ARMS, PushPull.PULL);
            case ABS -> new CustomTarget("abdominals", null, BodyPart.CORE, PushPull.PULL);
            case LOWER_BACK -> new CustomTarget("lower back", null, BodyPart.BACK, PushPull.PULL);
            case GLUTES -> new CustomTarget("glutes", null, BodyPart.LEGS, PushPull.PUSH);
            case LEGS -> {
                if (side == null) {
                    throw new IllegalArgumentException("하체는 앞허벅지·뒤허벅지를 골라야 한다");
                }
                yield side == Side.FRONT
                        ? new CustomTarget("quadriceps", null, BodyPart.LEGS, PushPull.PUSH)
                        : new CustomTarget("hamstrings", null, BodyPart.LEGS, PushPull.PULL);
            }
            case CALVES -> new CustomTarget("calves", null, BodyPart.LEGS, PushPull.PUSH);
            // 동작(밀기·당기기)은 기본 종목에서도 앞이 밀기·없음으로 갈려 어느 쪽도 아닌 것으로 둔다
            case NECK -> {
                if (side == null) {
                    throw new IllegalArgumentException("목은 앞·뒤를 골라야 한다");
                }
                yield side == Side.FRONT
                        ? new CustomTarget("front neck", null, BodyPart.NECK, PushPull.NONE)
                        : new CustomTarget("back neck", null, BodyPart.NECK, PushPull.NONE);
            }
        };
    }
}
