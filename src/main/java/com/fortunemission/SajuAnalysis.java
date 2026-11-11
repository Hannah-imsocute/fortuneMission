package com.fortunemission;

import java.util.Map;

public record SajuAnalysis(
        String yearPillar,
        String monthPillar,
        String dayPillar,
        String timePillar,
        Map<String, Integer> elements
) {
}
