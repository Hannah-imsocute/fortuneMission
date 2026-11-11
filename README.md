# Fortune Mission · 포춘미션

양력 생년월일·출생시간·성별 입력 → 서버 사주·오행 계산 → 기존 Ollama에서 운세와 퀘스트 3개 생성 → 화면 표시·달성 체크·답변 복사/다운로드.

**Java 21 / Spring Boot 3.5.16 / HTML·CSS·JavaScript / Ollama** 구성입니다. React, Node.js, MySQL, 회원가입, 채팅, 영구 저장은 사용하지 않습니다. Spring Boot 컨테이너 하나만 추가하며 기존 `ollamaContainer`를 재생성하지 않습니다.

## EC2에서 실행

필요한 도구는 Docker와 Docker Compose v2입니다. EC2에 Java나 Maven을 따로 설치할 필요는 없습니다. 저장소 루트에서 실행하세요.

```bash
cp .env.example .env
```

**요청대로 모델명은 비워 두었습니다.** 사용할 모델을 결정하면 기존 컨테이너에 설치하고 `.env`의 `OLLAMA_MODEL=`을 정확한 모델 태그로 채우세요.

```bash
# <모델명> 부분을 실제 사용할 모델 태그로 바꿉니다.
docker exec ollamaContainer ollama pull <모델명>
docker exec ollamaContainer ollama list
```

`.env` 설정:

```dotenv
OLLAMA_MODEL=
OLLAMA_BASE_URL=http://ollamaContainer:11434
OLLAMA_TIMEOUT=180s
APP_PORT=8080
OLLAMA_NETWORK=fortune-network
```

`OLLAMA_MODEL`이 비어 있어도 화면과 서버는 실행됩니다. 답변 생성은 모델을 설치하고 이름을 지정한 후 가능합니다. `/api/chat`과 JSON 스키마 출력을 지원하는 최신 Ollama를 사용하세요. 첫 요청은 모델 로딩으로 느릴 수 있습니다. 느린 CPU에서는 `OLLAMA_TIMEOUT=300s`처럼 늘릴 수 있습니다.

기존 Ollama와 앱을 같은 Docker 네트워크에 연결합니다.

```bash
# 네트워크가 없을 때 한 번만 생성합니다.
docker network create fortune-network

# ollamaContainer가 이 네트워크에 연결되어 있지 않을 때 한 번만 실행합니다.
docker network connect fortune-network ollamaContainer

docker compose up -d --build
docker compose logs -f app
```

이미 사용하는 사용자 정의 네트워크가 있다면 `.env`의 `OLLAMA_NETWORK`를 해당 이름으로 지정하고 위 생성/연결 과정은 생략해도 됩니다. 단, 해당 네트워크에서 `ollamaContainer`라는 이름으로 접근 가능해야 합니다. 컨테이너 사이에서는 `localhost`가 아닌 `http://ollamaContainer:11434`를 사용합니다. Ollama 포트를 EC2 외부로 공개할 필요는 없습니다.

브라우저에서 `http://EC2의-퍼블릭-IP:8080`을 엽니다. EC2 보안 그룹에서 앱 포트(기본 TCP 8080)에 접근할 수 있어야 합니다.

```bash
# 앱 기동 확인 (Ollama/모델까지 검사하는 엔드포인트는 아닙니다.)
curl -fsS http://localhost:8080/api/health

# 실제 모델까지 연결되는 전체 흐름 확인
curl -fsS http://localhost:8080/api/fortunes \
  -H 'Content-Type: application/json' \
  -d '{"birthDate":"2000-01-07","birthTime":"12:00","gender":"FEMALE"}'
```

모델명이나 `.env` 값을 변경하면 `docker compose up -d`로 앱 컨테이너에 반영합니다. 코드 변경 시 `docker compose up -d --build`를 실행하세요. 앱만 중지하려면 `docker compose down`을 사용합니다. 외부 네트워크와 기존 Ollama 컨테이너는 유지됩니다.

## 계산과 응답

- 입력은 **양력, 한국 출생, 고정 UTC+9**입니다. 음력 변환, 진태양시, 역사적 서머타임·UTC+8:30 보정은 포함하지 않습니다.
- `cn.6tail:lunar:1.7.8`로 연·월주의 절기 경계를 계산합니다. 라이브러리의 UTC+8 절기 기준에 맞춰 출생 시각을 변환합니다.
- 일주는 한국 시각 **00시**에 변경합니다. 23시 시주도 당일 일간을 사용합니다.
- 오행은 사주 8글자의 대표 오행을 센 값입니다. 지장간·강약 가중치·대운 계산은 생략합니다. 성별은 AI 해석 입력에 전달합니다.
- 오늘 날짜는 서버 위치와 관계없이 `Asia/Seoul` 기준입니다.
- LLM에는 서버 계산 결과와 오늘 날짜를 보내고, JSON 스키마로 운세 요약과 퀘스트 3개를 요청합니다. 실제 응답을 파싱해 결과만 돌려주며 잘못된 응답을 임의의 운세로 대체하지 않습니다.
- 일반 입력 검사는 브라우저 기본 `required`와 날짜/시간 입력만 사용합니다. 서버는 JSON 파싱 및 필수 항목 처리, Ollama 연결/응답 오류 처리만 둡니다.
- 달성 체크는 현재 화면에서만 유지됩니다. 새로고침하면 초기화됩니다. 입력과 답변은 DB·브라우저 저장소에 보관하지 않습니다. 답변은 화면에서 복사하거나 `.txt`로 다운로드할 수 있습니다.

`POST /api/fortunes` 입력:

```json
{"birthDate":"2000-01-07","birthTime":"12:00","gender":"FEMALE"}
```

`gender`는 `FEMALE` 또는 `MALE`입니다. 응답 형식 (운세 문구는 예시):

```json
{
  "date": "2026-11-11",
  "saju": {
    "yearPillar": "己卯", "monthPillar": "丁丑",
    "dayPillar": "甲子", "timePillar": "庚午",
    "elements": {"목": 2, "화": 2, "토": 2, "금": 1, "수": 1}
  },
  "summary": "오늘은 작은 일부터 차근차근 시작해 보세요.",
  "quests": ["10분 산책하기", "책상 위 정리하기", "오늘 느낀 점 세 줄 쓰기"]
}
```

오류는 `{"message":"원인과 조치 안내"}`로 반환합니다. 모델 미설정·연결 실패·시간 초과는 503, Ollama 오류·잘못된 생성 JSON은 502, 요청 JSON 오류·필수 항목 누락은 400입니다.

## 개발과 테스트

Java **JDK 21**과 Maven 3.9 이상이 있는 경우:

```bash
mvn clean verify
OLLAMA_BASE_URL=http://localhost:11434 OLLAMA_MODEL='<설치한 모델명>' mvn spring-boot:run
```

Docker 빌드도 `mvn clean verify`로 테스트 후 실행 JAR를 만듭니다. 실행 이미지에는 JRE와 JAR만 포함됩니다.

테스트는 알려진 사주, 입춘·월 절기·자정 경계 및 HTTP API → Ollama 요청 → 응답 추출을 확인합니다. 자동화 테스트의 Ollama는 모의 HTTP 서버이므로 모델 다운로드 없이 실행 가능합니다. 실제 모델의 응답 품질·속도·가용 메모리는 선택한 모델과 EC2 사양에 따라 달라집니다. 앱용 메모리와 Ollama 모델용 메모리를 함께 확보해야 합니다.

## 문제 확인

```bash
docker compose logs --tail=100 app
docker logs --tail=100 ollamaContainer
docker exec ollamaContainer ollama list
docker network inspect fortune-network
```

모델이 없다는 오류는 `ollama list`의 이름과 `.env`의 `OLLAMA_MODEL`을 일치시키세요. 연결 오류는 Ollama 실행 상태·네트워크·`OLLAMA_BASE_URL`을 확인하세요. 502 JSON 오류가 계속되면 JSON 형식 출력을 잘 따르는 모델로 변경하세요.

첨부된 계획서·중간보고서의 핵심 흐름을 반영하되, 요청한 최소 구성에 맞춰 프론트엔드 분리와 DB, 음력 입력 등을 생략했습니다.

## 구현 시 검증 결과

2026-11-11 기준 다음 항목을 확인했습니다.

- `mvn clean verify`: 테스트 18건 통과, 실패·오류·스킵 0건.
- Docker 이미지 빌드 및 Compose 실행 성공. 별도 컨테이너를 `ollamaContainer` 네트워크 별칭으로 연결하여 요청·응답 추출 확인.
- Chromium에서 실제 앱을 열어 입력 → 결과 표시 → 퀘스트 체크 → 답변 복사·다운로드 확인. 모바일 화면 가로 넘침과 JavaScript 오류 없음.
- 모델명을 비운 상태에서 앱 기동과 생성 요청의 설정 안내(503), 버튼 복구 확인.

**Ollama 응답 테스트는 모의 서버를 사용했습니다.** 이 개발 환경의 네트워크 정책이 `registry.ollama.ai` 접근을 403으로 차단하여 실제 모델 다운로드·추론은 검증하지 못했습니다. 사용자 EC2에 접속하거나 배포한 것은 아닙니다. EC2에서 모델 설치 후 위 전체 흐름 확인 요청을 실행하세요.
