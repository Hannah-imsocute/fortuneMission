package com.fortunemission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunemission.FortuneController.BirthRequest;
import com.fortunemission.FortuneController.Gender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FortuneApiTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SUMMARY = "오늘은 차분하게 계획을 정리해 보세요.";
    private static final List<String> QUESTS = List.of("산책 10분 하기", "물 한 잔 마시기", "책상 정리하기");
    private static final Map<String, String> INPUT = Map.of(
            "birthDate", "2000-01-07", "birthTime", "12:00", "gender", "FEMALE");
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final AtomicReference<JsonNode> CAPTURED = new AtomicReference<>();
    private static final AtomicReference<String> METHOD = new AtomicReference<>();
    private static final HttpServer OLLAMA = startOllama();

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void configureOllama(DynamicPropertyRegistry properties) {
        properties.add("ollama.base-url", FortuneApiTest::ollamaUrl);
        properties.add("ollama.model", () -> "test-model");
        properties.add("ollama.timeout", () -> "2s");
    }

    @BeforeEach
    void resetOllama() throws Exception {
        REPLY.set(new Reply(200, envelope(JSON.writeValueAsString(Map.of("summary", SUMMARY, "quests", QUESTS))), 0));
        CAPTURED.set(null);
        METHOD.set(null);
    }

    @AfterAll
    static void stopOllama() {
        OLLAMA.stop(0);
    }

    @Test
    void calculatesSajuSendsStructuredRequestAndExtractsFortune() throws Exception {
        LocalDate before = LocalDate.now(ZoneId.of("Asia/Seoul"));
        ResponseEntity<JsonNode> response = generate();
        LocalDate after = LocalDate.now(ZoneId.of("Asia/Seoul"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(LocalDate.parse(result.path("date").asText())).isIn(before, after);
        assertThat(result.path("summary").asText()).isEqualTo(SUMMARY);
        assertThat(JSON.convertValue(result.path("quests"), List.class)).containsExactlyElementsOf(QUESTS);
        assertThat(result.at("/saju/dayPillar").asText()).isEqualTo("甲子");
        assertThat(result.at("/saju/timePillar").asText()).isEqualTo("庚午");
        int elementCount = 0;
        for (JsonNode count : result.at("/saju/elements")) {
            elementCount += count.asInt();
        }
        assertThat(elementCount).isEqualTo(8);

        assertThat(METHOD.get()).isEqualTo("POST");
        JsonNode sent = CAPTURED.get();
        assertThat(sent.path("model").asText()).isEqualTo("test-model");
        assertThat(sent.path("stream").asBoolean(true)).isFalse();
        assertThat(sent.path("think").asBoolean(true)).isFalse();
        assertThat(sent.at("/format/properties/quests/minItems").asInt()).isEqualTo(3);
        assertThat(sent.at("/format/properties/quests/maxItems").asInt()).isEqualTo(3);
        assertThat(sent.at("/messages/0/role").asText()).isEqualTo("system");
        JsonNode prompt = JSON.readTree(sent.at("/messages/1/content").asText());
        assertThat(prompt.path("birthDate").asText()).isEqualTo("2000-01-07");
        assertThat(prompt.path("birthTime").asText()).isEqualTo("12:00");
        assertThat(prompt.path("gender").asText()).isEqualTo("FEMALE");
        assertThat(prompt.path("today").asText()).isEqualTo(result.path("date").asText());
        assertThat(prompt.path("saju")).isEqualTo(result.path("saju"));
    }

    @ParameterizedTest
    @MethodSource("invalidModelReplies")
    void rejectsMalformedOrIncompleteModelOutput(String body) {
        REPLY.set(new Reply(200, body, 0));

        ResponseEntity<JsonNode> response = generate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().path("message").asText()).contains("퀘스트 3개", "JSON");
    }

    static Stream<String> invalidModelReplies() throws Exception {
        return Stream.of(
                "not JSON",
                "{}",
                envelope(""),
                envelope("```json\n{\"summary\":\"text\",\"quests\":[]}\n```"),
                envelope(JSON.writeValueAsString(Map.of("summary", SUMMARY, "quests", List.of("한 개")))),
                envelope(JSON.writeValueAsString(Map.of("summary", " ", "quests", QUESTS))),
                envelope(JSON.writeValueAsString(Map.of("summary", SUMMARY, "quests", List.of("하나", " ", "셋")))));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void explainsOllamaHttpFailures(int status) {
        REPLY.set(new Reply(status, "{\"error\":\"test upstream failure\"}", 0));

        ResponseEntity<JsonNode> response = generate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().path("message").asText())
                .contains(status == 404 ? "모델을 찾을 수 없습니다" : "생성 요청을 처리하지 못했습니다");
    }

    @Test
    void explainsOllamaTimeout() {
        REPLY.set(new Reply(200, "{}", 3_000));

        ResponseEntity<JsonNode> response = generate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().path("message").asText()).contains("응답 시간이 초과");
    }

    @Test
    void rejectsMissingInputWithoutCallingOllama() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/fortunes", Map.of("birthDate", "2000-01-07"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("message").asText()).contains("생년월일, 출생시간, 성별");
        assertThat(CAPTURED.get()).isNull();
    }

    @Test
    void blankModelDoesNotCallOllama() {
        OllamaClient client = new OllamaClient(JSON, ollamaUrl(), "", Duration.ofSeconds(2));
        BirthRequest birth = new BirthRequest(LocalDate.of(2000, 1, 7), LocalTime.NOON, Gender.FEMALE);
        SajuAnalysis saju = new SajuCalculator().calculate(birth.birthDate(), birth.birthTime());

        assertThatThrownBy(() -> client.generate(birth, saju, LocalDate.of(2026, 11, 11)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).contains("OLLAMA_MODEL");
                });
        assertThat(CAPTURED.get()).isNull();
    }

    @Test
    void reportsApplicationHealthWithoutRequiringModelInference() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(CAPTURED.get()).isNull();
    }

    private ResponseEntity<JsonNode> generate() {
        return rest.postForEntity("/api/fortunes", INPUT, JsonNode.class);
    }

    private static String envelope(String content) throws Exception {
        return JSON.writeValueAsString(Map.of("message", Map.of("role", "assistant", "content", content), "done", true));
    }

    private static String ollamaUrl() {
        return "http://127.0.0.1:" + OLLAMA.getAddress().getPort();
    }

    private static HttpServer startOllama() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "test-ollama");
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/api/chat", exchange -> {
                Reply reply = REPLY.get();
                METHOD.set(exchange.getRequestMethod());
                CAPTURED.set(JSON.readTree(exchange.getRequestBody()));
                try {
                    Thread.sleep(reply.delayMillis());
                    byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(reply.status(), body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The client deliberately disconnects in the timeout scenario.
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start test Ollama server", exception);
        }
    }

    private record Reply(int status, String body, long delayMillis) { }
}
