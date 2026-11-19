package com.fitness.backend.importing.domain;

/** 이름 맞추기 결과(「기록가져오기_설계서」 4장) */
public enum MatchStatus {
    /** 내가 맞춘 짝·이름이 똑같음·미리 만든 짝 표로 맞음 */
    MATCHED,
    /** 계열 + 수식어로 짐작함. 추가를 막지 않는다(결정 10) */
    SIMILAR,
    /** 맞는 종목이 없음. 만들거나 골라야 추가할 수 있다 */
    UNREGISTERED
}
