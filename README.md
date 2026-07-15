# 온기(ongi) — 시니어 건강관리 앱 백엔드

> **부모님은 그냥 이야기만 하시면 됩니다.** 나머지는 온기가 합니다.

혼자 계신 부모님의 건강을 자녀가 챙기려면 매번 전화로 캐물어야 합니다.
온기는 **AI 에이전트가 어르신과 나눈 일상 대화에서 건강 정보를 자동으로 뽑아** 자녀의 대시보드에 보여줍니다.

---

## 핵심 기능

### 🗣 대화가 곧 건강 기록
어르신은 앱이 아니라 **대화**를 합니다. 서버가 대화에서 이런 것들을 알아서 추출합니다.

| 추출 항목 | 근거가 되는 대화 |
|---|---|
| 수면시간 | "어젯밤에 6시간쯤 잤어" |
| 운동량 | "아침에 산책 30분 했지" |
| 복약 여부 | "혈압약? 아침에 먹었어" |
| 질병 현재상황 | "요즘 혈압이 좀 높은 것 같아" |

> **설계 원칙 — 대화로 알아낼 수 있는 것만 저장합니다.**
> 걸음수처럼 대화로 알 수 없는 값은 넣지 않았습니다. 자가보고로 확인 가능한 것만 기록합니다.

### 📷 처방전은 사진 한 장으로
진단서·처방전을 촬영해 올리면 **OpenAI Vision** 이 판독해 약품명·용량·복용주기·질병을 **자동 등록**합니다.
어르신이 약 이름을 일일이 입력할 필요가 없습니다.

### 📊 자녀를 위한 통합 대시보드
API 한 번 호출로 화면이 완성됩니다 — 질병 현황, 오늘 복용한 약, 수면·운동, AI 상담 요약, 오늘의 체크리스트, 건강 점수.

### 🔔 질병·복약 기반 맞춤 리마인드
등록된 질병(ICD)·약(ATC) 코드를 규칙과 매칭해 어르신에게 필요한 알림만 보냅니다.
고혈압약을 드시는 분께만 "혈압약 드셨어요?" 를 묻습니다.

---

## 동작 흐름

```
어르신 ──대화──▶ AI 에이전트 ──LLM 추출──▶ 수면·운동·복약·질병 상황 ──┐
                                                                      ├──▶ 대시보드 ──▶ 자녀
진단서/처방전 사진 ──Vision 판독──▶ 질병·복약 자동 등록 ─────────────┘
```

---

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 프레임워크 | Java 21, Spring Boot 4.1 (Web MVC, Data JPA) |
| DB | MySQL 8 — 스키마는 SQL 스크립트가 소유 |
| 세션 | Redis (Spring Session) |
| 인증 | 세션 쿠키 + BCrypt |
| AI | OpenAI Chat Completions / Vision (`gpt-4o`) |
| 문서화 | springdoc-openapi (Swagger UI) |
| 로컬 인프라 | Docker Compose 자동 기동 |

---

## 실행

```bash
export OPENAI_API_KEY=sk-...   # AI 기능용 (없어도 서버는 정상 기동)
./gradlew bootRun              # MySQL·Redis 컨테이너 자동 기동
```

- 앱: `http://localhost:8080`
- **Swagger UI: http://localhost:8080/swagger-ui.html**
- DB: `localhost:13306` / `ongi` / `myuser` · `secret`

---

## API

**37개 엔드포인트 / 13개 그룹.** 모든 응답은 `{ success, message, data }` 로 감싸집니다.

| 그룹 | 설명 |
|---|---|
| Auth | 회원가입·로그인·로그아웃 (세션 쿠키) |
| Elders / Guardians | 어르신 관리, 가족 공유(M:N) |
| **Dashboard** | **통합 조회 — 상세 화면을 이 API 하나로 구성** |
| **Chat** | AI 에이전트 대화 (건강 컨텍스트 기반) |
| **Documents** | 진단서·처방전 사진 → Vision 판독 → 자동 등록 |
| **DailyLog** | 수면·운동·AI요약·복약 체크 |
| Diseases / Medications | 질병·복약 실데이터 |
| HealthNote | 마크다운 건강 컨텍스트 |
| Conversations / Check-in | 대화 기록, 일일 안부 문진 |
| Reminders | 질병·복약 매칭 맞춤 알림 |

📄 **프론트 연동 시 → [docs/frontend-api-guide.md](docs/frontend-api-guide.md)** (실제 DTO 기준 request/response, TypeScript 타입, axios 예제)

---

## 데이터 모델

테이블 10개. 스키마는 [schema.sql](src/main/resources/schema.sql) 이 단독 관리하며, 앱 실행 시 자동 반영됩니다.

| 테이블 | 역할 |
|---|---|
| `users` / `elders` / `guardian_elder` | 보호자, 어르신, 보호관계(M:N) |
| `elder_disease` / `elder_medication` | 질병·복약 실데이터 (ICD·ATC 코드) |
| `elder_health_note` | 어르신별 마크다운 건강 컨텍스트 |
| `agent_conversation` | 에이전트 대화 로그 (JSON) |
| `elder_daily_log` | 하루 생활 로그 (수면·운동·AI요약·체크리스트) |
| `elder_medication_intake` | 복약 여부 (약×일자) |
| `reminder_rule_master` | 사전 정의 리마인드 규칙 |

---

## 프로젝트 구조

```
src/main/java/com/example/demo/
├── common/       공통 응답·예외·세션 유틸
├── config/       OpenAPI, CORS, OpenAI 설정
├── controller/   REST 엔드포인트 (13)
├── service/      비즈니스 로직 (15)
├── domain/       엔티티 + enums
├── repository/   Spring Data JPA (10)
└── dto/          요청/응답 record
```
