package com.fitness.backend.importing.domain;

/**
 * 가져온 세트의 종류(「기록가져오기_설계서」 결정 4·13). 저장할 때 워밍업은 워밍업 세트, 드랍은 본세트(실제로 한 운동이고
 * 우리 앱에 드랍 표시는 없다), 실패는 실패 세트다(2026-11-28, 결정 기록 D-15) — `실패 35kg 5/10회`는 해낸 5회, 목표 10회.
 */
public enum SetKind {
    SET, WARMUP, DROP, FAIL;

    public boolean warmup() {
        return this == WARMUP;
    }
}
