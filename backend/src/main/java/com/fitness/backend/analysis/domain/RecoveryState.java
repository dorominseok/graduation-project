package com.fitness.backend.analysis.domain;

/**
 * 부위별 회복 상태 4단계. 기준은 「회복지도_설계서」 3장.
 *
 * <p>피로를 잰 값이 아니라 기록(날짜·세트 수)으로 짐작한 값이다. 그래서 "피로도"가 아니라
 * "회복 상태"라고 부른다.
 */
public enum RecoveryState {

    /** 회복 중이고, 그 기간에 루틴의 부위당 최대치 이상 했다. */
    HIGH("회복 중 · 많이 함"),
    /** 회복 중. 루틴이 오늘 빼는 부위다. */
    MEDIUM("회복 중"),
    /** 회복 기간이 막 끝났다 — 오늘 해도 되지만 아직 덜 쉰 편이다. */
    LOW("거의 회복"),
    /** 그보다 오래됐거나 최근 기록이 없다. */
    RECOVERED("회복됨");

    private final String label;

    RecoveryState(String label) {
        this.label = label;
    }

    /** 화면 표기용 한국어 라벨({@code stateLabel}). */
    public String label() {
        return label;
    }
}
