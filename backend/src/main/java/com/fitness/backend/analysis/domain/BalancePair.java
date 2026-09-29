package com.fitness.backend.analysis.domain;

/**
 * 맞대어 보는 두 쪽의 짝. API 명세서 8.3 {@code pairs[]}.
 *
 * <p>선언 순서가 응답 순서다.
 */
public enum BalancePair {

    PUSH_PULL("밀기 / 당기기", BalanceSide.PUSH, BalanceSide.PULL),
    UPPER_LOWER("상체 / 하체", BalanceSide.UPPER, BalanceSide.LOWER);

    private final String label;
    private final BalanceSide left;
    private final BalanceSide right;

    BalancePair(String label, BalanceSide left, BalanceSide right) {
        this.label = label;
        this.left = left;
        this.right = right;
    }

    public String label() {
        return label;
    }

    public BalanceSide left() {
        return left;
    }

    public BalanceSide right() {
        return right;
    }
}
