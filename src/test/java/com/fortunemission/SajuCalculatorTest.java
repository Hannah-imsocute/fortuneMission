package com.fortunemission;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SajuCalculatorTest {
    private final SajuCalculator calculator = new SajuCalculator();

    @Test
    void calculatesKnownGapjaDayAndEightVisibleElements() {
        SajuAnalysis result = at("2000-01-07", "12:00");

        assertThat(result.yearPillar()).isEqualTo("己卯");
        assertThat(result.monthPillar()).isEqualTo("丁丑");
        assertThat(result.dayPillar()).isEqualTo("甲子");
        assertThat(result.timePillar()).isEqualTo("庚午");
        assertThat(result.elements()).isEqualTo(Map.of("목", 2, "화", 2, "토", 2, "금", 1, "수", 1));
    }

    @Test
    void changesYearAndMonthAtIpchunInKoreaTime() {
        // 2024년 입춘은 한국 표준시 2월 4일 17시 27분 무렵이다.
        SajuAnalysis before = at("2024-02-04", "17:20");
        SajuAnalysis after = at("2024-02-04", "17:35");

        assertThat(before.yearPillar()).isEqualTo("癸卯");
        assertThat(before.monthPillar()).isEqualTo("乙丑");
        assertThat(after.yearPillar()).isEqualTo("甲辰");
        assertThat(after.monthPillar()).isEqualTo("丙寅");
        assertThat(after.dayPillar()).isEqualTo(before.dayPillar());
    }

    @Test
    void changesMonthAtGyeongchipWithoutChangingYear() {
        // 2024년 경칩은 한국 표준시 3월 5일 11시 23분 무렵이다.
        SajuAnalysis before = at("2024-03-05", "11:00");
        SajuAnalysis after = at("2024-03-05", "12:00");

        assertThat(before.monthPillar()).isEqualTo("丙寅");
        assertThat(after.monthPillar()).isEqualTo("丁卯");
        assertThat(after.yearPillar()).isEqualTo(before.yearPillar());
    }

    @Test
    void changesDayAndTimeStemAtMidnightRatherThanAt23Hours() {
        SajuAnalysis lateNight = at("2000-01-07", "23:30");
        SajuAnalysis midnight = at("2000-01-08", "00:30");

        assertThat(lateNight.dayPillar()).isEqualTo("甲子");
        assertThat(lateNight.timePillar()).isEqualTo("甲子");
        assertThat(midnight.dayPillar()).isEqualTo("乙丑");
        assertThat(midnight.timePillar()).isEqualTo("丙子");
    }

    private SajuAnalysis at(String date, String time) {
        return calculator.calculate(LocalDate.parse(date), LocalTime.parse(time));
    }
}
