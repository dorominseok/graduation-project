package com.fitness.backend.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fitness.backend.analysis.domain.VolumeVerdict;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@code application.yaml}의 값이 실제로 바인딩되고, 그 값으로 판정이 명세대로
 * 동작하는지 확인한다. 설정과 계산이 분리돼 있으므로 이어지는 지점을 검증한다.
 */
@SpringBootTest
class AnalysisPropertiesTest {

    @Autowired
    AnalysisProperties props;

    @Test
    @DisplayName("명세 8.5의 상수가 그대로 바인딩된다")
    void bindsSpecConstants() {
        assertEquals(4, props.weeks());
        assertEquals(0, props.volume().insufficientBelow().compareTo(BigDecimal.valueOf(4)));
        assertEquals(0, props.volume().optimalFrom().compareTo(BigDecimal.valueOf(10)));
        assertEquals(0, props.volume().optimalThrough().compareTo(BigDecimal.valueOf(20)));
        assertEquals(0, props.balanceRatioThreshold().compareTo(new BigDecimal("2.0")));
        assertEquals(6, props.confidenceSessionThreshold());
        assertEquals(12, props.oneRmMaxReps());
        assertEquals(Duration.ofMinutes(15), props.interSetCap());
        assertEquals(Duration.ofSeconds(90), props.lastSetBonus());
        assertEquals(Duration.ofHours(4), props.sessionCap());
    }

    @Test
    @DisplayName("집계 구간은 기준일 포함 28일 — 하한 오프셋은 27이다")
    void periodIsTwentyEightDaysInclusive() {
        assertEquals(27, props.periodDaysBack());
    }

    @Test
    @DisplayName("설정에서 만든 값 객체로 판정하면 경계가 명세와 같다")
    void thresholdsDriveVerdict() {
        var t = props.volumeThresholds();
        assertEquals(VolumeVerdict.INSUFFICIENT, VolumeVerdict.classify(new BigDecimal("3.9"), t));
        assertEquals(VolumeVerdict.BELOW_RECOMMENDED, VolumeVerdict.classify(new BigDecimal("4.0"), t));
        assertEquals(VolumeVerdict.OPTIMAL, VolumeVerdict.classify(new BigDecimal("10.0"), t));
        assertEquals(VolumeVerdict.OPTIMAL, VolumeVerdict.classify(new BigDecimal("20.0"), t));
        assertEquals(VolumeVerdict.EXCESSIVE, VolumeVerdict.classify(new BigDecimal("20.1"), t));
    }

    @Test
    @DisplayName("운동 시간 보정도 설정에서 그대로 넘어간다")
    void durationPolicyFromConfig() {
        var p = props.durationPolicy();
        assertEquals(Duration.ofMinutes(15), p.interSetCap());
        assertEquals(Duration.ofSeconds(90), p.lastSetBonus());
        assertEquals(Duration.ofHours(4), p.sessionCap());
    }
}
