package com.fortunemission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunemission.FortuneController.BirthRequest;
import com.fortunemission.FortuneController.GeneratedFortune;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OllamaClient {
    private static final String SYSTEM_PROMPT = """
            당신은 포춘미션의 한국어 운세 도우미입니다. 제공된 사주와 오행 개수는 서버 계산 결과입니다.
            이를 다시 계산하거나 바꾸지 마세요. 오늘 날짜를 기준으로 성향과 오행 균형을 참고하여
            따뜻한 운세 요약과 오늘 실천할 수 있는 구체적이고 서로 다른 퀘스트를 정확히 3개 작성하세요.
            요약은 한국어 2~3문장, 퀘스트는 한국어 한 문장씩 작성하세요.
            운세는 가벼운 자기성찰용으로 제시하고 미래를 단정하거나 의료·투자 조언을 하지 마세요.
            summary 문자열과 quests 문자열 배열만 포함한 JSON 객체로 답하세요.
            """;
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "summary":{"type":"string","minLength":1},
              "quests":{"type":"array","minItems":3,"maxItems":3,
                        "items":{"type":"string","minLength":1}}
            },"required":["summary","quests"],"additionalProperties":false}
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;

    public OllamaClient(ObjectMapper mapper,
                        @Value("${ollama.base-url}") String baseUrl,
                        @Value("${ollama.model}") String model,
                        @Value("${ollama.timeout}") Duration timeout) {
        this.mapper = mapper;
        this.model = model;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public GeneratedFortune generate(BirthRequest birth, SajuAnalysis saju, LocalDate today) {
        if (model.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "모델이 설정되지 않았습니다. .env의 OLLAMA_MODEL에 Ollama에 설치한 모델명을 입력하세요.");
        }
        try {
            String prompt = mapper.writeValueAsString(Map.of(
                    "today", today.toString(), "birthDate", birth.birthDate().toString(),
                    "birthTime", birth.birthTime().toString(), "gender", birth.gender().name(),
                    "saju", saju));
            var body = Map.of(
                    "model", model, "stream", false, "think", false,
                    "format", mapper.readTree(SCHEMA),
                    "messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt)),
                    "options", Map.of("temperature", 0.7, "num_ctx", 4096, "num_predict", 1024));
            JsonNode response = client.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("message").path("content").isTextual()) {
                throw invalidResponse();
            }
            JsonNode content = mapper.readTree(response.path("message").path("content").asText());
            if (content == null || !content.path("summary").isTextual()
                    || content.path("summary").asText().isBlank()
                    || !content.path("quests").isArray() || content.path("quests").size() != 3) {
                throw invalidResponse();
            }
            List<String> quests = StreamSupport.stream(content.path("quests").spliterator(), false)
                    .map(node -> {
                        if (!node.isTextual() || node.asText().isBlank()) {
                            throw invalidResponse();
                        }
                        return node.asText().strip();
                    }).toList();
            return new GeneratedFortune(content.path("summary").asText().strip(), quests);
        } catch (RestClientResponseException exception) {
            String message = exception.getStatusCode().value() == 404
                    ? "Ollama 모델을 찾을 수 없습니다. 모델을 pull하고 OLLAMA_MODEL을 확인하세요."
                    : "Ollama가 생성 요청을 처리하지 못했습니다. 컨테이너 로그와 모델 설정을 확인하세요.";
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ollama에 연결할 수 없거나 응답 시간이 초과되었습니다. 컨테이너 연결과 OLLAMA_TIMEOUT을 확인하세요.");
        } catch (JsonProcessingException | RestClientException exception) {
            throw invalidResponse();
        }
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "모델이 운세와 퀘스트 3개를 올바른 JSON으로 반환하지 않았습니다. 다시 생성하거나 모델을 변경하세요.");
    }
}
