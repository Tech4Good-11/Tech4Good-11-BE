# 온기(ongi) 프론트엔드 API 연동 가이드

구현된 백엔드(Spring Boot)의 **실제 DTO 기준** request/response 모델 문서. 프론트는 이 문서대로 타입을 맞추면 된다.
소스: `src/main/java/com/example/demo/dto/**`, `controller/**` / 상세 설명은 Swagger(`/swagger-ui.html`)에도 동일 반영.

> 최종 확인: 구현 코드 기준. 값/필드가 Swagger와 다르면 **이 문서와 코드가 우선**.

---

## A. 기획 개요

**온기(ongi)** — 자녀(보호자)가 에이전트를 통해 어르신(시니어)의 건강을 관리하는 앱.

- **보호자(User)**: 로그인 주체. 어르신을 등록/관리한다.
- **어르신(Elder)**: 관리 대상. 자체 로그인 없음(보호자가 소유).
- 한 어르신을 여러 보호자가 공유 가능(M:N).

### 핵심 화면 ↔ 엔드포인트 매핑
| 화면 | 주 엔드포인트 |
|---|---|
| 로그인/회원가입 | `POST /auth/signup`, `POST /auth/login` |
| 앱 진입(세션확인) | `GET /auth/me` |
| 홈(어르신 목록) | `GET /elders` |
| 어르신 추가 | `POST /elders` |
| 어르신 상세 대시보드 | `GET /elders/{id}/dashboard` ← **한 번에 로드** |
| 건강정보 탭(질병/복약) | `GET/POST/PUT/DELETE /elders/{id}/diseases`, `.../medications` |
| 건강노트 | `GET/PUT /elders/{id}/health-note` |
| 진단서/처방전 업로드 | `POST /elders/{id}/documents` (OpenAI Vision 실구현) |
| 안부 확인 / 체크리스트 | `GET /elders/{id}/checkin/today` → `POST /elders/{id}/checkin` |
| 알림(리마인드) | `GET /elders/{id}/reminders` |
| 대화 이력 | `GET /elders/{id}/conversations`, `GET /conversations/{id}` |
| 가족 공유 | `GET/POST/DELETE /elders/{id}/guardians` |
| **AI 대화 — 어르신 모드** | `POST /elders/{id}/chat` (어르신 본인이 말할 때) |
| **AI 상담 — 자녀 모드** | `POST /elders/{id}/consult` (자녀가 부모님 상태를 물을 때) |
| **수면/운동 지표** | 대시보드 `dailyLog` (개별: `GET/PUT /elders/{id}/daily-log`) |
| **복약 체크박스** | `POST /elders/{id}/medication-intake` |

---

## B. 공통 규약

### B-1. 기본
- Base URL: `http://localhost:8080`, 모든 경로 프리픽스 `/api` (아래 표기는 `/api` 생략)
- 요청/응답 JSON 필드: **camelCase**
- 날짜: `LocalDate` → `"YYYY-MM-DD"`, `LocalDateTime` → `"YYYY-MM-DDTHH:mm:ss"`
- Content-Type: `application/json` (문서 업로드만 `multipart/form-data`)

### B-2. 인증 (세션 쿠키)
1. `POST /api/auth/login` 성공 → 응답 헤더로 **세션 쿠키(SESSION)** 발급.
2. 이후 인증이 필요한 모든 요청에 쿠키 동봉:
   - axios: `axios.create({ baseURL, withCredentials: true })`
   - fetch: `fetch(url, { credentials: 'include' })`
3. 앱 진입 시 `GET /api/auth/me` 로 세션 유효성 확인 → 401 이면 로그인 화면.
4. CORS: 서버가 `allowCredentials=true` 로 열려 있음(개발용 전체 허용).

### B-3. 공통 응답 포맷 (`ApiResponse<T>`)
**모든** 응답은 아래로 감싸진다.
```jsonc
// 성공
{ "success": true,  "message": "OK", "data": <T> }
// 실패 (예외 → 전역 핸들러)
{ "success": false, "message": "리소스를 찾을 수 없습니다.", "data": null }
```
아래 각 엔드포인트의 "응답"은 **`data` 내부만** 표기한다.

```typescript
// 공통 타입
interface ApiResponse<T> { success: boolean; message: string; data: T | null; }
```

### B-4. 에러 코드
| HTTP | 상황 | message(기본) |
|---|---|---|
| 400 | 잘못된 입력/이메일 중복 | `잘못된 입력입니다.` 등 |
| 401 | 로그인 필요·자격증명 오류 | `인증이 필요합니다.` / `이메일 또는 비밀번호가 올바르지 않습니다.` |
| 403 | 내가 돌보지 않는 어르신 접근 | `권한이 없습니다.` |
| 404 | 리소스 없음 | `리소스를 찾을 수 없습니다.` |
| 409 | 중복(공동보호자 중복 등) | `이미 등록된 ...` |
| 500 | 서버 오류 | `서버 오류가 발생했습니다.` |

프론트는 `success===false` 면 `message` 를 사용자 토스트로 노출하면 된다.

### B-5. Enum 사전 (직렬화 값 = 소문자 그대로)
| Enum | 값 |
|---|---|
| gender | `M` `F` `other` |
| relationship | `son` `daughter` `spouse` `sibling` `relative` `caregiver` `other` |
| conversation.purpose | `daily_checkin` `document_intake` `free` |
| disease.status | `active` `managed` `resolved` |
| medication.status | `active` `stopped` `completed` |
| reminder.ruleType | `medication` `hydration` `meal` `vital_check` `custom` |
| reminder.matchTarget | `disease` `medication` `all` |
| reminder.frequencyType | `interval_hours` `daily` `weekly` |
| expectedResponse | `yes_no` `none` |

---

## C. 엔드포인트별 모델

### 1. Auth

#### `POST /auth/signup` — 회원가입 (인증 불필요)
```jsonc
// Request
{ "email": "parent@example.com", "password": "plain-pw", "name": "김기훈", "phone": "010-1234-5678" }
// phone 은 선택(null 가능)
```
```jsonc
// data (UserResponse)
{ "id": 1, "email": "parent@example.com", "name": "김기훈", "phone": "010-1234-5678", "createdAt": "2026-07-15T10:00:00" }
```
- 이메일 중복 시 400.

#### `POST /auth/login` — 로그인 (인증 불필요)
```jsonc
// Request
{ "email": "parent@example.com", "password": "plain-pw" }
// data: UserResponse (위와 동일). 세션 쿠키 발급.
```
- 실패 401.

#### `POST /auth/logout` — 로그아웃 (인증 필요)
- Request 없음. `data: null`, message `"로그아웃되었습니다."`

#### `GET /auth/me` — 내 정보 (인증 필요)
- `data: UserResponse`. 세션 없으면 401.

```typescript
interface UserResponse { id: number; email: string; name: string; phone: string | null; createdAt: string; }
```

---

### 2. Elders

#### `POST /elders` — 어르신 등록 (인증 필요)
등록과 동시에 로그인 보호자와의 보호관계 생성.
```jsonc
// Request (ElderCreateRequest)
{ "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "phone": "010-5555-6666", "relationship": "son" }
// birthDate/gender/phone 선택, relationship 필수
```
```jsonc
// data (ElderResponse)
{ "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M",
  "phone": "010-5555-6666", "relationship": "son", "createdAt": "2026-07-15T10:00:00" }
```

#### `GET /elders` — 내 어르신 목록 (인증 필요)
```jsonc
// data: ElderSummaryResponse[]
[ { "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "relationship": "son",
    "activeMedicationCount": 2, "activeDiseaseCount": 1, "lastCheckinAt": "2026-07-15T09:00:00" } ]
// lastCheckinAt: 안부 이력 없으면 null
```

#### `GET /elders/{elderId}` — 상세 (인증+소유권)
- `data: ElderResponse`

#### `PUT /elders/{elderId}` — 수정 (인증+소유권)
```jsonc
// Request (ElderUpdateRequest) — relationship 제외
{ "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "phone": "010-5555-6666" }
// data: ElderResponse
```

#### `DELETE /elders/{elderId}` — 삭제 (인증+소유권)
- 연관 데이터 CASCADE 삭제. `data: null`.

```typescript
interface ElderResponse { id: number; name: string; birthDate: string | null; gender: 'M'|'F'|'other'|null; phone: string | null; relationship: Relationship; createdAt: string; }
interface ElderSummaryResponse { id: number; name: string; birthDate: string | null; gender: Gender|null; relationship: Relationship; activeMedicationCount: number; activeDiseaseCount: number; lastCheckinAt: string | null; }
type Gender = 'M'|'F'|'other';
type Relationship = 'son'|'daughter'|'spouse'|'sibling'|'relative'|'caregiver'|'other';
```

---

### 3. Guardians  (`/elders/{elderId}/guardians`)

#### `GET` — 보호자 목록
```jsonc
// data: GuardianResponse[]
[ { "userId": 1, "email": "parent@example.com", "name": "김기훈", "phone": "010-1234-5678", "relationship": "son" } ]
```
#### `POST` — 공동 보호자 추가
```jsonc
// Request (GuardianAddRequest) — 대상은 이미 가입된 계정이어야 함
{ "email": "other@example.com", "relationship": "daughter" }
// data: GuardianResponse. 이미 보호자면 409.
```
#### `DELETE /{userId}` — 보호관계 해제
- `data: null`.

---

### 4. Dashboard

#### `GET /elders/{elderId}/dashboard` — 통합 대시보드 (인증+소유권)
```jsonc
// data (DashboardResponse)
{
  "elder": { "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M" },
  "healthNote": { "contentMd": "## 최근 상태\n- 혈압 안정...", "updatedAt": "2026-07-15T08:00:00" }, // 없으면 null
  "diseases":    [ /* DiseaseResponse[] : 질병 현재상황 = status + notes */ ],
  "medications": [ /* MedicationResponse[] */ ],
  "todayReminders": [ /* ElderReminderResponse[] : 체크리스트 원천(하드코딩 불필요) */ ],
  "recentCheckins": [ { "conversationId": 1, "purpose": "daily_checkin", "createdAt": "2026-07-15T09:00:00",
                        "summary": null } ],   // ⚠️ 대부분 null. AI 요약은 아래 dailyLog.conditionSummary 를 쓸 것

  // ↓↓↓ 대화에서 추출된 오늘 지표 (신규)
  "dailyLog": {
    "elderId": 1, "logDate": "2026-07-16",
    "sleepHours": 6.5,            // 수면시간(시간). null이면 '기록 없음'
    "exerciseMinutes": 30,        // 운동량(분). ⚠️ 걸음수 아님. null이면 '기록 없음'
    "conditionSummary": "산책 30분, 약 복용",
    "checklist": [ { "ruleCode": "HTN_MED_CHECK", "answer": "yes" } ],  // 체크박스 상태 복원용
    "sourceConversationId": 11, "updatedAt": "2026-07-16T09:10:00"
  },
  "todayMedications": [           // 오늘치 복용한 약 (활성 약 전체 + 복용여부)
    { "medicationId": 1, "medicationName": "암로디핀", "dosage": "5mg 1정",
      "taken": true,              // null이면 아직 미확인
      "intakeDate": "2026-07-16" }
  ],
  "healthScore": {                // 서버 계산 파생값(게이지바용)
    "score": 96,                  // 총점 0~100. 근거 없으면 null → '기록 없음'
    "medicationScore": 100, "sleepScore": 88, "exerciseScore": 100,
    "comment": "오늘 건강 관리가 잘 되고 있어요."
  }
}
```

> **프론트 이슈 해결 매핑**
> - "오늘 운동"/"어젯밤 수면" 하드코딩 → `dailyLog.exerciseMinutes` / `dailyLog.sleepHours` (null이면 '기록 없음')
> - 체크리스트 하드코딩 → `todayReminders`(질문 목록) + `dailyLog.checklist`(체크 상태).
>   **약/질병을 아직 등록하지 않은 신규 어르신에게도 기본 항목(물 마시기·식사)이 항상 내려온다** → 로컬 폴백 하드코딩 불필요
> - 체크박스 미연동 → `POST /elders/{id}/checkin` 또는 `POST /elders/{id}/medication-intake`
> - AI 상담 요약 빈값 → **`dailyLog.conditionSummary` 를 쓸 것.**
>   `recentCheckins[].summary` 는 **쓰지 마세요** — 요약은 하루 단위로 저장되는데 그 요약을 만들어낸 대화 1건에만 붙어서,
>   최신 대화(`recentCheckins[0]`)는 보통 null 입니다.
> - AI 점수 게이지 하드코딩 → `healthScore.score` (+ 항목별 점수)

---

### 5. HealthNote  (`/elders/{elderId}/health-note`)

#### `GET` — 조회
```jsonc
// data (HealthNoteResponse) — 없으면 data: null
{ "elderId": 1, "contentMd": "## 최근 상태\n- ...", "createdAt": "...", "updatedAt": "..." }
```
#### `PUT` — 갱신(upsert)
```jsonc
// Request { "contentMd": "## 최근 상태\n- ..." }
// data: HealthNoteResponse
```

---

### 6. Diseases  (`/elders/{elderId}/diseases`)

#### `GET` — 목록  (`?status=active|managed|resolved` 선택)
```jsonc
// data: DiseaseResponse[]
[ { "id": 1, "diseaseName": "본태성 고혈압", "icdCode": "I10", "diagnosedAt": "2023-05-10", "status": "managed", "notes": "아침 혈압 다소 높음" } ]
```
#### `POST` — 추가 / `PUT /{diseaseId}` — 수정
```jsonc
// Request (DiseaseRequest) — icdCode/diagnosedAt/notes 선택, status 기본 active
{ "diseaseName": "제2형 당뇨병", "icdCode": "E11", "diagnosedAt": "2022-11-02", "status": "active", "notes": null }
// data: DiseaseResponse
```
#### `DELETE /{diseaseId}` — 삭제 → `data: null`

```typescript
interface DiseaseResponse { id: number; diseaseName: string; icdCode: string | null; diagnosedAt: string | null; status: 'active'|'managed'|'resolved'; notes: string | null; }
```

---

### 7. Medications  (`/elders/{elderId}/medications`)

#### `GET` — 목록  (`?status=active|stopped|completed` 선택)
```jsonc
// data: MedicationResponse[]
[ { "id": 1, "medicationName": "암로디핀", "atcCode": "C08CA01", "dosage": "5mg 1정",
    "intervalHours": 24, "startDate": "2023-05-10", "endDate": null, "status": "active" } ]
```
#### `POST` — 추가 / `PUT /{medicationId}` — 수정
```jsonc
// Request (MedicationRequest) — atcCode/dosage/intervalHours/startDate/endDate 선택, status 기본 active
{ "medicationName": "메트포르민", "atcCode": "A10BA02", "dosage": "500mg 1정",
  "intervalHours": 12, "startDate": "2022-11-02", "endDate": null, "status": "active" }
// data: MedicationResponse
```
#### `DELETE /{medicationId}` — 삭제 → `data: null`

```typescript
interface MedicationResponse { id: number; medicationName: string; atcCode: string | null; dosage: string | null; intervalHours: number | null; startDate: string | null; endDate: string | null; status: 'active'|'stopped'|'completed'; }
```

---

### 8. Documents  ✅ 실구현 (OpenAI Vision)

#### `POST /elders/{elderId}/documents` — 진단서/처방전 업로드·처리
- Content-Type: `multipart/form-data`
  - `file`: 이미지(jpg/png 권장)
  - `docType`: `diagnosis` | `prescription`
```jsonc
// data (DocumentIntakeResponse)
{
  "conversationId": 10,
  "docType": "prescription",
  "extractedMedications": [ /* MedicationResponse[] : 새로 등록된 복약 */ ],
  "extractedDiseases":    [ /* DiseaseResponse[]    : 새로 등록된 질병 */ ],
  "healthNoteUpdated": true
}
```
> **실제 동작**: 업로드한 이미지를 **OpenAI Vision 으로 판독**해 약품/질병을 추출하고 DB에 실제 등록한다.
> 결과는 이미지 내용에 따라 달라진다(고정 예시 아님). 프론트는 응답 결과 표시 후 대시보드/목록을 새로고침.
> ⚠️ 서버에 `OPENAI_API_KEY` 가 있어야 동작(미설정 시 500). 판독 실패 시 400 + "문서를 인식하지 못했습니다".
> 업로드 예시(axios):
> ```js
> const fd = new FormData(); fd.append('file', file); fd.append('docType', 'prescription');
> axios.post(`/api/elders/${id}/documents`, fd, { withCredentials: true });
> ```

---

### 9. Conversations

> ⚠️ **혼동 주의**: 이 그룹은 **완성된 대화를 저장/조회**하는 API다. **AI 응답을 생성하지 않는다.**
> AI가 답하는 화면은 **[12] `/chat`(어르신) 또는 `/consult`(자녀)** 를 써야 한다.

#### `POST /elders/{elderId}/conversations` — 대화 저장
```jsonc
// Request (ConversationCreateRequest) — transcript 는 임의 JSON
{ "purpose": "daily_checkin",
  "transcript": [ { "role": "agent", "text": "약 드셨어요?" }, { "role": "elder", "answer": "yes" } ] }
// data (ConversationSummaryResponse)
{ "id": 11, "elderId": 1, "purpose": "daily_checkin", "createdAt": "2026-07-15T09:00:00" }
```
#### `GET /elders/{elderId}/conversations` — 목록  (`?purpose=&page=0&size=20`)
```jsonc
// data: ConversationSummaryResponse[]  (transcript 제외, 배열만 반환 — page 메타 없음)
```
#### `GET /conversations/{conversationId}` — 상세 (인증+소유권)
```jsonc
// data (ConversationDetailResponse) — transcript 포함
{ "id": 11, "elderId": 1, "purpose": "daily_checkin",
  "transcript": [ /* 저장된 JSON 그대로 */ ], "createdAt": "2026-07-15T09:00:00" }
```

---

### 10. Check-in  (`/elders/{elderId}/checkin`)

#### `GET /today` — 오늘의 문진 항목
```jsonc
// data: CheckinTodayResponse[]
[ { "ruleCode": "HTN_MED_CHECK", "question": "홍길동님, 혈압약 드셨어요?", "expectedResponse": "yes_no", "scheduledTimes": ["09:00"] } ]
```
#### `POST` — 문진 응답 제출
```jsonc
// Request (CheckinSubmitRequest)
{ "answers": [ { "ruleCode": "HTN_MED_CHECK", "answer": "yes" }, { "ruleCode": "HYDRATION_ALL", "answer": "no" } ] }
// data (CheckinSubmitResponse)
{ "conversationId": 12, "savedAt": "2026-07-15T09:05:00" }
```

---

### 11. Reminders

#### `GET /reminder-rules` — 규칙 마스터(참조/관리용, 인증 필요)  (`?ruleType=&isActive=`)
```jsonc
// data: ReminderRuleResponse[]
[ { "id": 1, "ruleCode": "HTN_MED_CHECK", "ruleType": "medication", "matchTarget": "medication",
    "matchCode": "C08CA01", "frequencyType": "daily", "frequencyValue": "09:00",
    "messageTemplate": "{name}님, 혈압약 드셨어요?", "expectedResponse": "yes_no", "isActive": true } ]
```
#### `GET /elders/{elderId}/reminders` — 어르신 적용 리마인드
```jsonc
// data: ElderReminderResponse[]  ({name} → 어르신 이름 치환됨)
[ { "ruleCode": "HTN_MED_CHECK", "ruleType": "medication", "message": "홍길동님, 혈압약 드셨어요?",
    "frequencyType": "daily", "times": ["09:00"], "expectedResponse": "yes_no",
    "matchedBy": { "target": "medication", "code": "C08CA01", "medicationName": "암로디핀", "diseaseName": null } } ]
```
> **매칭 규칙**: `matchedBy.target`
> - `medication`/`disease` — 어르신의 약(atcCode)·질병(icdCode)이 규칙과 맞을 때만 내려온다.
> - `all` — **모든 어르신에게 항상 내려온다.** 약/질병을 하나도 등록하지 않은 신규 어르신도 기본 항목
>   (`HYDRATION_ALL` 물 마시기, `DAILY_MEAL` 식사)을 받는다 → **빈 배열 대비 하드코딩 폴백이 필요 없다.**

---

### 12. Chat / Consult — AI 대화 (OpenAI)

> 🔴 **AI 대화는 화면에 따라 API 가 다르다. 절대 섞어 쓰면 안 된다.**
>
> | | **`/chat`** | **`/consult`** |
> |---|---|---|
> | 누가 말하나 | **어르신 본인** | **자녀(보호자)** |
> | 쓰는 화면 | 어르신 모드의 대화/안부 | 자녀 모드의 AI 상담 |
> | AI 말투 | 어르신에게 2인칭 ("옥자님, ...") | 자녀에게 3인칭 ("박옥자님께서는...") |
> | 대화 저장 | ✅ | ❌ |
> | 건강지표 추출 | ✅ (자가보고니까) | ❌ |
>
> **자녀 화면에서 `/chat` 을 호출하면 안 됩니다.** 이 API 는 발화자를 어르신으로 간주하므로,
> 자녀의 말이 **어르신 발화로 저장**되고 **어르신의 수면·운동 기록으로 잘못 추출**됩니다.

#### `POST /elders/{elderId}/chat` — 어르신이 AI와 대화 (**어르신 전용**)
어르신의 질병·복약·건강노트를 컨텍스트로 넣어 '온기' 에이전트가 어르신에게 답한다.
```jsonc
// Request (ChatRequest)
{
  "message": "어젯밤에 잘 못 잤어요",          // 필수
  "history": [                                  // 선택: 이전 맥락
    { "role": "user", "content": "안녕하세요" },
    { "role": "assistant", "content": "안녕하세요, 홍길동님!" }
  ],
  "purpose": "free",                            // 선택: daily_checkin | document_intake | free (기본 free)
  "save": true                                  // 선택: true면 대화 저장 + 지표 자동 추출
}
// data (ChatResponse)
{ "reply": "많이 힘드셨겠어요. 몇 시간 정도 주무셨어요?", "conversationId": 11 }  // save=false면 conversationId=null
```
> `save=true` 로 보내면 대화가 저장되고, **수면·운동·복약·질병 현재상황이 자동 추출**되어 대시보드에 반영된다(추출 실패해도 대화는 정상 응답).
> 어르신 대화는 `save=true` 로 보내는 것을 권장한다. 그래야 대시보드 지표가 채워진다.

#### `POST /elders/{elderId}/consult` — 자녀가 부모님 상태를 AI와 상담 (**자녀 전용**)
어르신이 남긴 기록(**최근 대화**·건강노트·질병·복약·수면/운동 추이)을 근거로 AI가 자녀에게 답한다.
```jsonc
// Request (ConsultRequest)
{
  "message": "어머니가 머리 아프다고 하셨다던데, 제가 뭘 하면 좋을까요?",  // 필수
  "history": [                                  // 선택: 이전 상담 맥락
    { "role": "user", "content": "어머니 요즘 어떠세요?" },
    { "role": "assistant", "content": "박옥자님께서는 최근..." }
  ]
}
// data (ConsultResponse) — 저장하지 않으므로 conversationId 가 없다
{ "reply": "박옥자님께서는 어제부터 머리가 아프고 4시간밖에 주무시지 못한 상태입니다. 먼저..." }
```
> **AI 가 어르신의 실제 대화 기록을 근거로 답한다.** 어르신이 `/chat` 에서 "머리가 아파요" 라고 말한 적이 있으면,
> 자녀가 물었을 때 그 기록을 참조해 답변한다. 기록에 없는 내용은 지어내지 않는다.
>
> ⚠️ **상담은 저장되지 않는다.** 대화 맥락을 이어가려면 프론트가 `history` 로 직접 넘겨야 한다.

```typescript
interface ChatRequest { message: string; history?: {role: 'user'|'assistant'; content: string}[]; purpose?: ConversationPurpose; save?: boolean; }
interface ChatResponse { reply: string; conversationId: number | null; }
interface ConsultRequest { message: string; history?: {role: 'user'|'assistant'; content: string}[]; }
interface ConsultResponse { reply: string; }
```

> ⚠️ `/chat`·`/consult` 모두 서버에 `OPENAI_API_KEY` 필요(미설정 시 500 + 안내 메시지).

---

### 13. DailyLog — 하루 지표 / 복약 체크

#### `GET /elders/{elderId}/daily-log?date=YYYY-MM-DD` — 조회 (date 생략 시 오늘)
```jsonc
// data (DailyLogResponse) — 대시보드의 dailyLog 와 동일 구조
{ "elderId": 1, "logDate": "2026-07-16", "sleepHours": 6.5, "exerciseMinutes": 30,
  "conditionSummary": "산책 30분, 약 복용",
  "checklist": [ { "ruleCode": "HTN_MED_CHECK", "answer": "yes" } ],
  "sourceConversationId": 11, "updatedAt": "2026-07-16T09:10:00" }
```

#### `PUT /elders/{elderId}/daily-log` — 수동 저장/수정 (부분 수정)
```jsonc
// Request — null 필드는 변경 안 함. logDate 생략 시 오늘.
{ "logDate": null, "sleepHours": 6.5, "exerciseMinutes": 30, "conditionSummary": "산책 30분" }
// data: DailyLogResponse
```

#### `POST /elders/{elderId}/daily-log/extract?conversationId=11` — 대화에서 재추출 (OpenAI)
- `conversationId` 생략 시 최근 대화. 챗봇 `save=true` 면 자동 추출되므로 **보정/재분석용**.
- `data`: DailyLogResponse. ⚠️ `OPENAI_API_KEY` 필요.

#### `GET /elders/{elderId}/medication-intake?date=` — 오늘치 복용한 약
```jsonc
// data: MedicationIntakeResponse[]  (대시보드 todayMedications 와 동일)
[ { "medicationId": 1, "medicationName": "암로디핀", "dosage": "5mg 1정", "taken": true, "intakeDate": "2026-07-16" } ]
```

#### `POST /elders/{elderId}/medication-intake` — 복약 체크 (체크박스 연동)
```jsonc
// Request — 같은 약/같은 날은 덮어쓰기(upsert)
{ "medicationId": 1, "taken": true, "intakeDate": null }   // intakeDate 생략 시 오늘
// data: MedicationIntakeResponse
```

```typescript
interface DailyLogResponse { elderId: number; logDate: string; sleepHours: number | null; exerciseMinutes: number | null; conditionSummary: string | null; checklist: {ruleCode: string; answer: 'yes'|'no'}[]; sourceConversationId: number | null; updatedAt: string | null; }
interface MedicationIntakeResponse { medicationId: number; medicationName: string; dosage: string | null; taken: boolean | null; intakeDate: string; }
interface HealthScore { score: number | null; medicationScore: number | null; sleepScore: number | null; exerciseScore: number | null; comment: string; }
```

---

## D. 구현 현실 주의사항 (프론트가 반드시 알아야 함)

1. **POST 직후 응답의 `createdAt`/`savedAt`가 `null`일 수 있음** — DB엔 정상 저장되나 생성 직후 응답엔 타임스탬프가 안 채워진다. 값이 필요하면 이후 GET(목록/상세)에서 받는다.
2. **목록은 배열만 반환** — conversations 목록도 `page/size` 파라미터는 받지만 응답 `data`는 **배열**(총개수/페이지 메타 없음). 무한스크롤은 size 기반으로 처리.
2-1. **`recentCheckins[].summary` 는 쓰지 말 것** — 대부분 null 이다. AI 상담 요약은 **`dailyLog.conditionSummary`** 가 정답. (요약은 하루 단위 저장이라 대화 1건에만 연결됨)
3. **Documents는 실구현(OpenAI Vision)** — 업로드한 이미지를 실제로 판독한다. 인식 실패 시 400이 날 수 있으니 에러 UI를 준비할 것. (`OPENAI_API_KEY` 필요)
4. **시드 계정 로그인 불가** — `parent1@example.com` 등 시드 유저는 더미 해시라 로그인 안 됨. 테스트는 `signup`으로 새 계정 생성 후 진행.
5. **transcript는 자유 JSON** — 서버는 형태를 강제하지 않는다. 프론트/에이전트가 `[{role, text|answer}, ...]` 컨벤션을 합의해 사용.
6. **소유권 403** — 다른 보호자의 elderId로 접근하면 403. elderId는 항상 `GET /elders` 로 받은 것만 사용.
7. **⚠️ 걸음수는 제공하지 않는다** — 운동량은 대화 자가보고 기반이라 **`exerciseMinutes`(분)** 만 있다. 걸음수는 대화로 알 수 없어(웨어러블 필요) 스키마에 없다. 화면 문구를 "오늘 운동 30분"으로 잡을 것.
8. **`healthScore` 는 파생값** — 대화로 얻는 값이 아니라 복약 순응도·수면·운동으로 **서버가 계산**한다. 근거 데이터가 없는 항목은 `null`이고 총점 평균에서 제외되며, 전부 없으면 `score=null`(→ '기록 없음' 표시). 산식: 복약=복용/확인된약, 수면=7시간 기준 1시간당 -25, 운동=30분이면 100.
9. **`null` = 기록 없음** — `sleepHours`/`exerciseMinutes`/`taken`/`score`가 null이면 "아직 대화나 입력으로 확인되지 않음"이다. 0과 구분할 것(0은 "안 했음"이 확인된 상태).
10. **OpenAI 필요 API** — `/chat`, `/consult`, `/documents`, `/daily-log/extract` 는 서버에 `OPENAI_API_KEY` 가 있어야 동작. 나머지 API는 키 없이도 정상.
11. **🔴 `/chat` 과 `/consult` 를 섞어 쓰지 말 것** — `/chat` 은 **발화자를 어르신으로 간주**한다. 자녀 화면에서 `/chat` 을 호출하면 자녀의 말이 어르신 발화로 저장되고 **어르신의 수면·운동 기록으로 잘못 추출**되어 대시보드 데이터가 오염된다. 자녀 화면은 반드시 `/consult`. (자세한 비교는 [12] 참고)
12. **`/consult` 는 저장되지 않는다** — 상담 맥락을 이어가려면 프론트가 `history` 로 이전 대화를 직접 넘겨야 한다. 새로고침하면 상담 내용은 사라진다.

---

## E. 프론트 공통 클라이언트 예시 (axios)

```typescript
import axios from 'axios';

export const api = axios.create({ baseURL: 'http://localhost:8080/api', withCredentials: true });

// 응답 언랩 + 에러 표준화
api.interceptors.response.use(
  (res) => {
    const body = res.data; // ApiResponse<T>
    if (body && body.success === false) return Promise.reject(new Error(body.message));
    return body?.data ?? body;
  },
  (err) => {
    const msg = err.response?.data?.message ?? '네트워크 오류';
    if (err.response?.status === 401) { /* 로그인 화면으로 리다이렉트 */ }
    return Promise.reject(new Error(msg));
  }
);

// 사용 예
// const me = await api.get<UserResponse>('/auth/me');
// const elders = await api.get<ElderSummaryResponse[]>('/elders');
```
