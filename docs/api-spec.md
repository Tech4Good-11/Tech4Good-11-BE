# 온기(ongi) API 명세서 (MVP)

시니어 건강관리 앱 백엔드. 자녀(보호자)가 에이전트를 통해 시니어(어르신)의 건강을 관리한다.

- Base URL: `http://localhost:8080`
- 문서(Swagger UI): `http://localhost:8080/swagger-ui.html`
- 인증: **세션 기반**(Spring Session + Redis). 로그인 시 발급되는 세션 쿠키(`SESSION`)를 이후 요청에 함께 전송한다. (프론트는 `fetch/axios` 에서 `credentials: 'include'`)

---

## 1. 공통 규약

### 1.1 응답 포맷 (`ApiResponse<T>`)

모든 응답은 아래 래퍼로 감싼다.

```json
// 성공
{ "success": true,  "message": "OK", "data": { /* 실제 페이로드 */ } }

// 실패 (예외 → GlobalExceptionHandler 가 변환)
{ "success": false, "message": "리소스를 찾을 수 없습니다.", "data": null }
```

아래 각 엔드포인트의 "응답" 항목은 `data` 안에 들어가는 값만 표기한다.

### 1.2 에러 코드 (`ErrorCode`)

| HTTP | 코드 | 의미 |
|---|---|---|
| 400 | `INVALID_INPUT` | 잘못된 입력(검증 실패) |
| 401 | `UNAUTHORIZED` | 로그인 필요(세션 없음/만료) |
| 403 | `FORBIDDEN` | 권한 없음(내가 보호하지 않는 시니어 접근 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

### 1.3 규칙

- 요청/응답 JSON 필드는 **camelCase**(예: `birthDate`, `createdAt`). DB 컬럼(snake_case)과 매핑된다.
- 날짜: `DATE`는 `YYYY-MM-DD`, `DATETIME`은 ISO-8601 `YYYY-MM-DDTHH:mm:ss`.
- `passwordHash` 등 민감 필드는 **응답에 절대 포함하지 않는다**.
- **소유권 검증**: 로그인한 보호자가 `guardian_elder`로 연결되지 않은 시니어의 리소스에 접근하면 `403 FORBIDDEN`.
- 목록 조회는 필요 시 `?page=0&size=20` 페이지네이션(기본 size=20).

---

## 2. 인증 / 보호자 계정 (Auth)  ← `users`

### `POST /api/auth/signup` — 회원가입
- 인증: 불필요
- Request
```json
{ "email": "parent@example.com", "password": "plain-password", "name": "김기훈", "phone": "010-1234-5678" }
```
- 응답(data): 생성된 보호자
```json
{ "id": 1, "email": "parent@example.com", "name": "김기훈", "phone": "010-1234-5678", "createdAt": "2026-07-15T10:00:00" }
```
- 실패: `400`(이메일 형식/누락), `409`는 사용 안 함 → 이메일 중복은 `INVALID_INPUT`(400)로 "이미 가입된 이메일입니다."

### `POST /api/auth/login` — 로그인
- 인증: 불필요
- Request `{ "email": "parent@example.com", "password": "plain-password" }`
- 응답(data): 로그인한 보호자 정보(위 signup 응답과 동일 형태). 세션 쿠키 발급.
- 실패: `401 UNAUTHORIZED`("이메일 또는 비밀번호가 올바르지 않습니다.")

### `POST /api/auth/logout` — 로그아웃
- 인증: 필요 → 세션 무효화
- 응답(data): `null`, message `"로그아웃되었습니다."`

### `GET /api/auth/me` — 내 정보
- 인증: 필요
- 응답(data): 현재 로그인한 보호자 정보

---

## 3. 시니어 관리 (Elders)  ← `elders`, `guardian_elder`

### `POST /api/elders` — 시니어 등록
등록과 동시에 로그인 보호자와의 보호관계(`guardian_elder`)를 함께 생성한다.
- 인증: 필요
- Request
```json
{ "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "phone": "010-5555-6666", "relationship": "son" }
```
  - `gender`: `M` | `F` | `other`
  - `relationship`: `son` | `daughter` | `spouse` | `sibling` | `relative` | `caregiver` | `other`
- 응답(data)
```json
{ "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "phone": "010-5555-6666", "relationship": "son", "createdAt": "2026-07-15T10:00:00" }
```

### `GET /api/elders` — 내가 보호하는 시니어 목록
- 인증: 필요
- 응답(data): 배열. 각 항목에 대시보드 진입용 요약 포함(최근 안부 상태 등)
```json
[
  { "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M", "relationship": "son",
    "activeMedicationCount": 2, "activeDiseaseCount": 1, "lastCheckinAt": "2026-07-15T09:00:00" }
]
```

### `GET /api/elders/{elderId}` — 시니어 상세
- 인증: 필요 + 소유권 검증
- 응답(data): 시니어 기본 정보

### `PUT /api/elders/{elderId}` — 시니어 정보 수정
- Request: 등록과 동일(단 `relationship` 제외)
- 응답(data): 수정된 시니어

### `DELETE /api/elders/{elderId}` — 시니어 삭제
- 인증: 필요 + 소유권 검증. 연관 데이터(FK CASCADE)도 함께 삭제됨.
- 응답(data): `null`, message `"삭제되었습니다."`

### (선택) 공동 보호자 — `guardian_elder`
한 시니어를 여러 보호자가 관리(M:N)하는 케이스.
- `GET /api/elders/{elderId}/guardians` — 이 시니어의 보호자 목록
- `POST /api/elders/{elderId}/guardians` — 공동 보호자 추가 `{ "email": "other@example.com", "relationship": "daughter" }`
- `DELETE /api/elders/{elderId}/guardians/{userId}` — 보호관계 해제

---

## 4. 대시보드 (Dashboard)

### `GET /api/elders/{elderId}/dashboard` — 통합 대시보드
건강 컨텍스트(마크다운) + 질병/복약 실데이터 + 최근 안부 + 오늘의 리마인드를 한 번에 조회.
- 인증: 필요 + 소유권 검증
- 응답(data)
```json
{
  "elder": { "id": 1, "name": "홍길동", "birthDate": "1945-03-11", "gender": "M" },
  "healthNote": { "contentMd": "## 최근 상태\n- 혈압 안정...", "updatedAt": "2026-07-15T08:00:00" },
  "diseases": [ { "id": 1, "diseaseName": "본태성 고혈압", "icdCode": "I10", "status": "managed" } ],
  "medications": [ { "id": 1, "medicationName": "암로디핀", "atcCode": "C08CA01", "dosage": "5mg 1정", "intervalHours": 24, "status": "active" } ],
  "todayReminders": [ { "ruleCode": "HTN_MED_CHECK", "ruleType": "medication", "message": "홍길동님, 혈압약 드셨어요?", "times": ["09:00"], "expectedResponse": "yes_no" } ],
  "recentCheckins": [ { "conversationId": 1, "purpose": "daily_checkin", "createdAt": "2026-07-15T09:00:00", "summary": "약 복용 확인(yes)" } ]
}
```

---

## 5. 건강 컨텍스트 (Health Note)  ← `elder_health_note` (시니어당 1개)

### `GET /api/elders/{elderId}/health-note`
- 응답(data): `{ "elderId": 1, "contentMd": "## 최근 상태\n...", "createdAt": "...", "updatedAt": "..." }`
- 없으면 `data: null`(또는 빈 노트)

### `PUT /api/elders/{elderId}/health-note` — 갱신(없으면 생성, upsert)
LLM이 대화/문서 처리 후 갱신하거나 보호자가 수동 편집.
- Request: `{ "contentMd": "## 최근 상태\n- ..." }`
- 응답(data): 갱신된 노트

---

## 6. 질병 (Diseases)  ← `elder_disease`

### `GET /api/elders/{elderId}/diseases`
- Query(선택): `?status=active|managed|resolved`
- 응답(data): 배열
```json
[ { "id": 1, "diseaseName": "본태성 고혈압", "icdCode": "I10", "diagnosedAt": "2023-05-10", "status": "managed", "notes": "아침 혈압 다소 높음" } ]
```

### `POST /api/elders/{elderId}/diseases` — 질병 추가(수동)
- Request
```json
{ "diseaseName": "제2형 당뇨병", "icdCode": "E11", "diagnosedAt": "2022-11-02", "status": "active", "notes": null }
```
  - `status`: `active`(치료중) | `managed`(관리중) | `resolved`(완치/종료), 기본 `active`
- 응답(data): 생성된 질병

### `PUT /api/elders/{elderId}/diseases/{diseaseId}` — 수정
### `DELETE /api/elders/{elderId}/diseases/{diseaseId}` — 삭제

---

## 7. 복약 (Medications)  ← `elder_medication`

### `GET /api/elders/{elderId}/medications`
- Query(선택): `?status=active|stopped|completed`
- 응답(data): 배열
```json
[ { "id": 1, "medicationName": "암로디핀", "atcCode": "C08CA01", "dosage": "5mg 1정",
    "intervalHours": 24, "startDate": "2023-05-10", "endDate": null, "status": "active" } ]
```

### `POST /api/elders/{elderId}/medications` — 복약 추가(수동)
- Request
```json
{ "medicationName": "메트포르민", "atcCode": "A10BA02", "dosage": "500mg 1정",
  "intervalHours": 12, "startDate": "2022-11-02", "endDate": null, "status": "active" }
```
  - `status`: `active`(복용중) | `stopped`(중단) | `completed`(처방완료), 기본 `active`
- 응답(data): 생성된 복약

### `PUT /api/elders/{elderId}/medications/{medicationId}` — 수정
### `DELETE /api/elders/{elderId}/medications/{medicationId}` — 삭제

---

## 8. 문서 처리 (Document Intake)  ← `agent_conversation` + `elder_disease`/`elder_medication`

진단서/처방전 **사진을 업로드**하면 에이전트가 OCR·해석하여, 결과를 `elder_disease`/`elder_medication` 실데이터로 저장하고 처리 과정을 `agent_conversation`(`purpose=document_intake`)에 남긴다.

### `POST /api/elders/{elderId}/documents` — 진단서/처방전 업로드·처리
- 인증: 필요 + 소유권 검증
- Content-Type: `multipart/form-data`
  - `file`: 이미지 파일 (jpg/png/pdf)
  - `docType`: `diagnosis`(진단서) | `prescription`(처방전)
- 처리: 비동기 가능하나 MVP는 동기 처리 후 결과 반환
- 응답(data)
```json
{
  "conversationId": 10,
  "docType": "prescription",
  "extractedMedications": [
    { "id": 3, "medicationName": "아빌리파이", "atcCode": "N05AX12", "dosage": "5mg", "intervalHours": 24, "status": "active" }
  ],
  "extractedDiseases": [],
  "healthNoteUpdated": true
}
```
- 실패: `400`(파일 없음/형식 오류), `422`는 사용 안 함 → 인식 실패는 `INVALID_INPUT`("문서를 인식하지 못했습니다.")

---

## 9. 에이전트 대화 (Conversations)  ← `agent_conversation`

에이전트와 시니어(또는 보호자)의 대화를 세션 단위로 저장. `transcript`는 JSON blob.

### `POST /api/elders/{elderId}/conversations` — 대화 기록 저장
- Request
```json
{
  "purpose": "daily_checkin",
  "transcript": [
    { "role": "agent", "text": "홍길동님, 오늘 혈압약 드셨어요?" },
    { "role": "elder", "answer": "yes" }
  ]
}
```
  - `purpose`: `daily_checkin`(일일 안부문진) | `document_intake`(문서 처리) | `free`(자유대화)
- 응답(data): `{ "id": 11, "elderId": 1, "purpose": "daily_checkin", "createdAt": "2026-07-15T09:00:00" }`

### `GET /api/elders/{elderId}/conversations` — 대화 목록
- Query(선택): `?purpose=daily_checkin&page=0&size=20`
- 응답(data): 요약 배열(transcript 제외) `[ { "id": 11, "purpose": "daily_checkin", "createdAt": "..." } ]`

### `GET /api/conversations/{conversationId}` — 대화 상세
- 인증: 필요 + 소유권 검증(대화의 elder를 보호하는지)
- 응답(data): transcript 포함 전체

---

## 10. 일일 안부문진 (Daily Check-in)

리마인드 규칙에 따라 에이전트가 시니어에게 Yes/No 문진을 하고 응답을 수집. 결과는 `agent_conversation`(`daily_checkin`)으로 저장.

### `GET /api/elders/{elderId}/checkin/today` — 오늘의 문진 항목
- 응답(data): 오늘 물어봐야 할 문항 목록(리마인드 규칙 매칭 결과)
```json
[ { "ruleCode": "HTN_MED_CHECK", "question": "홍길동님, 혈압약 드셨어요?", "expectedResponse": "yes_no", "scheduledTimes": ["09:00"] } ]
```

### `POST /api/elders/{elderId}/checkin` — 문진 응답 제출
- Request
```json
{ "answers": [ { "ruleCode": "HTN_MED_CHECK", "answer": "yes" }, { "ruleCode": "HYDRATION_ALL", "answer": "no" } ] }
```
- 처리: 응답을 `agent_conversation` 에 저장, 필요 시 `elder_health_note` 갱신
- 응답(data): `{ "conversationId": 12, "savedAt": "2026-07-15T09:05:00" }`

---

## 11. 리마인드 규칙 (Reminders)  ← `reminder_rule_master`

리마인드는 사전 정의된 규칙(`reminder_rule_master`)과 시니어의 질병/복약 실데이터를 매칭해 산출한다.

### `GET /api/reminder-rules` — 규칙 마스터 목록(참조용)
- 인증: 필요
- Query(선택): `?ruleType=medication&isActive=true`
- 응답(data)
```json
[ { "id": 1, "ruleCode": "HTN_MED_CHECK", "ruleType": "medication", "matchTarget": "medication",
    "matchCode": "C08CA01", "frequencyType": "daily", "frequencyValue": "09:00",
    "messageTemplate": "{name}님, 혈압약 드셨어요?", "expectedResponse": "yes_no", "isActive": true } ]
```
  - `ruleType`: `medication` | `hydration` | `meal` | `vital_check` | `custom`
  - `matchTarget`: `disease` | `medication` | `all`
  - `frequencyType`: `interval_hours` | `daily` | `weekly`

### `GET /api/elders/{elderId}/reminders` — 이 시니어에게 적용되는 리마인드
질병(`icd_code`)·복약(`atc_code`) 실데이터를 규칙의 `matchTarget`/`matchCode`와 매칭한 결과. 알림 문구는 `{name}` 치환.
- 응답(data)
```json
[
  { "ruleCode": "HTN_MED_CHECK", "ruleType": "medication", "message": "홍길동님, 혈압약 드셨어요?",
    "frequencyType": "daily", "times": ["09:00"], "expectedResponse": "yes_no",
    "matchedBy": { "target": "medication", "code": "C08CA01", "medicationName": "암로디핀" } }
]
```

---

## 12. 부록 — ENUM 정리

| 필드 | 값 |
|---|---|
| `elders.gender` | `M`(남) / `F`(여) / `other`(기타·미상) |
| `guardian_elder.relationship` | `son` / `daughter` / `spouse` / `sibling` / `relative` / `caregiver` / `other` |
| `agent_conversation.purpose` | `daily_checkin` / `document_intake` / `free` |
| `elder_disease.status` | `active`(치료중) / `managed`(관리중) / `resolved`(완치) |
| `elder_medication.status` | `active`(복용중) / `stopped`(중단) / `completed`(처방완료) |
| `reminder_rule_master.rule_type` | `medication` / `hydration` / `meal` / `vital_check` / `custom` |
| `reminder_rule_master.match_target` | `disease` / `medication` / `all` |
| `reminder_rule_master.frequency_type` | `interval_hours` / `daily` / `weekly` |
| `reminder_rule_master.expected_response` | `yes_no` / `none` |

---

## 13. 엔드포인트 요약

| 그룹 | 메서드 | 경로 | 설명 |
|---|---|---|---|
| Auth | POST | `/api/auth/signup` | 회원가입 |
| Auth | POST | `/api/auth/login` | 로그인 |
| Auth | POST | `/api/auth/logout` | 로그아웃 |
| Auth | GET | `/api/auth/me` | 내 정보 |
| Elders | POST | `/api/elders` | 시니어 등록 |
| Elders | GET | `/api/elders` | 내 시니어 목록 |
| Elders | GET | `/api/elders/{elderId}` | 시니어 상세 |
| Elders | PUT | `/api/elders/{elderId}` | 시니어 수정 |
| Elders | DELETE | `/api/elders/{elderId}` | 시니어 삭제 |
| Guardians | GET | `/api/elders/{elderId}/guardians` | 공동 보호자 목록 |
| Guardians | POST | `/api/elders/{elderId}/guardians` | 공동 보호자 추가 |
| Guardians | DELETE | `/api/elders/{elderId}/guardians/{userId}` | 보호관계 해제 |
| Dashboard | GET | `/api/elders/{elderId}/dashboard` | 통합 대시보드 |
| HealthNote | GET | `/api/elders/{elderId}/health-note` | 건강 컨텍스트 조회 |
| HealthNote | PUT | `/api/elders/{elderId}/health-note` | 건강 컨텍스트 갱신 |
| Diseases | GET | `/api/elders/{elderId}/diseases` | 질병 목록 |
| Diseases | POST | `/api/elders/{elderId}/diseases` | 질병 추가 |
| Diseases | PUT | `/api/elders/{elderId}/diseases/{diseaseId}` | 질병 수정 |
| Diseases | DELETE | `/api/elders/{elderId}/diseases/{diseaseId}` | 질병 삭제 |
| Medications | GET | `/api/elders/{elderId}/medications` | 복약 목록 |
| Medications | POST | `/api/elders/{elderId}/medications` | 복약 추가 |
| Medications | PUT | `/api/elders/{elderId}/medications/{medicationId}` | 복약 수정 |
| Medications | DELETE | `/api/elders/{elderId}/medications/{medicationId}` | 복약 삭제 |
| Documents | POST | `/api/elders/{elderId}/documents` | 진단서/처방전 업로드·처리 |
| Conversations | POST | `/api/elders/{elderId}/conversations` | 대화 기록 저장 |
| Conversations | GET | `/api/elders/{elderId}/conversations` | 대화 목록 |
| Conversations | GET | `/api/conversations/{conversationId}` | 대화 상세 |
| Check-in | GET | `/api/elders/{elderId}/checkin/today` | 오늘의 문진 항목 |
| Check-in | POST | `/api/elders/{elderId}/checkin` | 문진 응답 제출 |
| Reminders | GET | `/api/reminder-rules` | 규칙 마스터 목록 |
| Reminders | GET | `/api/elders/{elderId}/reminders` | 시니어 적용 리마인드 |
