package com.fitness.backend.importing.domain;

/**
 * 가져온 세트의 종류(「기록가져오기_설계서」 결정 4·13). 저장할 때 워밍업만 워밍업 세트가 되고, 드랍·실패는 본세트다 —
 * 둘 다 실제로 한 운동이고 우리 앱에 드랍·실패 표시는 없다. 실패 세트는 해낸 횟수로 들어간다.
 * 실패를 따로 두는 것은 나중에 실패 세트 기능을 만들면 그대로 잇기 위해서다.
 */
public enum SetKind {
    SET, WARMUP, DROP, FAIL;

    public boolean warmup() {
        return this == WARMUP;
    }
}
