package com.fortunemission;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.util.LunarUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SajuCalculator {
    public SajuAnalysis calculate(LocalDate birthDate, LocalTime birthTime) {
        LocalDateTime koreaTime = LocalDateTime.of(birthDate, birthTime);
        // lunar-java의 절기 시각은 UTC+8이므로 한국 출생 시각에서 1시간을 뺀다.
        Lunar solarTerms = lunarAt(koreaTime.minusHours(1));
        Lunar localDay = lunarAt(koreaTime);

        String year = solarTerms.getYearInGanZhiExact();
        String month = solarTerms.getMonthInGanZhiExact();
        String day = localDay.getDayInGanZhiExact2();

        // 일주는 한국 표준시 00시에 바뀐다. 23시에도 당일 일간으로 시주를 계산한다.
        int branch = LunarUtil.getTimeZhiIndex(birthTime.toString());
        int stem = (localDay.getDayGanIndexExact2() % 5 * 2 + branch) % 10;
        String time = LunarUtil.GAN[stem + 1] + LunarUtil.ZHI[branch + 1];

        Map<String, Integer> elements = new LinkedHashMap<>();
        for (String element : List.of("목", "화", "토", "금", "수")) {
            elements.put(element, 0);
        }
        for (String pillar : List.of(year, month, day, time)) {
            for (char character : pillar.toCharArray()) {
                elements.merge(elementOf(character), 1, Integer::sum);
            }
        }
        return new SajuAnalysis(year, month, day, time, Collections.unmodifiableMap(elements));
    }

    private Lunar lunarAt(LocalDateTime dateTime) {
        return Solar.fromYmdHms(dateTime.getYear(), dateTime.getMonthValue(),
                dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(),
                dateTime.getSecond()).getLunar();
    }

    // 천간과 지지 8글자의 대표 오행을 센다. 지장간·가중치는 포함하지 않는다.
    private String elementOf(char character) {
        return switch (character) {
            case '甲', '乙', '寅', '卯' -> "목";
            case '丙', '丁', '巳', '午' -> "화";
            case '戊', '己', '丑', '辰', '未', '戌' -> "토";
            case '庚', '辛', '申', '酉' -> "금";
            case '壬', '癸', '子', '亥' -> "수";
            default -> throw new IllegalArgumentException("지원하지 않는 간지: " + character);
        };
    }
}
