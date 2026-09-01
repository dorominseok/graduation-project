package com.fitness.backend.analysis.domain;

public enum BalanceVerdict {

    BALANCED("정상"),
    IMBALANCED("불균형"),
    INSUFFICIENT_DATA("판정 불가");

    private final String label;

    BalanceVerdict(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
