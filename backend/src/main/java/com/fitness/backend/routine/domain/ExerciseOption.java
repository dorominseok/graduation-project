package com.fitness.backend.routine.domain;

import com.fitness.backend.exercise.domain.Equipment;

/**
 * 후보를 고를 때 필요한 종목 정보와 이 사용자와의 관계.
 *
 * @param recentDays 최근 {@code history-weeks}주에 이 종목을 기록한 날 수. 0이면 안 해 본 것으로 본다
 * @param firstTime   오늘 이전에 이 종목 세트가 하나도 없다
 * @param starterRank 입문 대표 종목 순위. 아니면 null
 */
public record ExerciseOption(Long id,
                             String groupName,
                             Equipment equipment,
                             boolean favorite,
                             int recentDays,
                             boolean firstTime,
                             Integer starterRank) {
}
