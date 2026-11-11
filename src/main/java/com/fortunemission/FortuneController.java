package com.fortunemission;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FortuneController {
    private final SajuCalculator calculator;
    private final OllamaClient ollama;

    public FortuneController(SajuCalculator calculator, OllamaClient ollama) {
        this.calculator = calculator;
        this.ollama = ollama;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/fortunes")
    public ResponseEntity<FortuneResponse> generate(@RequestBody BirthRequest request) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        SajuAnalysis saju = calculator.calculate(request.birthDate(), request.birthTime());
        GeneratedFortune result = ollama.generate(request, saju, today);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new FortuneResponse(today, saju, result.summary(), result.quests()));
    }

    public enum Gender { FEMALE, MALE }

    public record BirthRequest(LocalDate birthDate, LocalTime birthTime, Gender gender) {
        public BirthRequest {
            Objects.requireNonNull(birthDate);
            Objects.requireNonNull(birthTime);
            Objects.requireNonNull(gender);
        }
    }

    public record GeneratedFortune(String summary, List<String> quests) { }

    public record FortuneResponse(LocalDate date, SajuAnalysis saju, String summary, List<String> quests) { }
}
