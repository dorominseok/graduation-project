package com.fitness.backend.importing.domain;

/**
 * 가져온 세트의 종류(「기록가져오기_설계서」 결정 4). 저장할 때 워밍업만 워밍업 세트가 되고, 드랍은 본세트다 —
 * 드랍세트는 실제 운동량이고 우리 앱에 드랍 표시는 없다.
 */
public enum SetKind {
    SET, WARMUP, DROP;

    public boolean warmup() {
        return this == WARMUP;
    }
}
