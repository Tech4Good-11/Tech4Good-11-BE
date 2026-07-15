-- =====================================================================
--  MVP 개발용 시드(Seed) 데이터
--  schema.sql 실행 직후(spring.sql.init) 매 시작마다 함께 실행되지만,
--  모든 INSERT 를 "INSERT IGNORE" 로 작성해 멱등(idempotent)하게 만든다.
--    - 첫 부팅        : PK/UNIQUE 충돌이 없으므로 시드가 삽입된다.
--    - 이후 재부팅    : 동일 PK 가 이미 있어 무시된다. -> 개발자가 넣은
--                       데이터도, 시드도 그대로 보존된다.
--    - RESET 후 부팅  : schema.sql 의 초기화 블록으로 테이블이 비면
--                       시드가 다시 삽입된다.
--  FK 연결을 결정적으로 하기 위해 PK 를 명시적으로 지정한다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- users : 보호자 2명  (password_hash 는 예시용 더미 bcrypt 해시)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO users (id, email, password_hash, name, phone) VALUES
  (1, 'parent1@example.com', '$2b$10$abcdefghijklmnopqrstuv1234567890abcdefghijklmnopqr', '김기훈', '010-1111-2222'),
  (2, 'parent2@example.com', '$2b$10$abcdefghijklmnopqrstuv1234567890abcdefghijklmnopqr', '이서연', '010-3333-4444');

-- ---------------------------------------------------------------------
-- elders : 시니어 2명
-- ---------------------------------------------------------------------
INSERT IGNORE INTO elders (id, name, birth_date, gender, phone) VALUES
  (1, '홍길동', '1945-03-11', 'M', '010-5555-6666'),
  (2, '박순자', '1950-08-22', 'F', '010-7777-8888');

-- ---------------------------------------------------------------------
-- guardian_elder : 보호관계
--   김기훈 -> 홍길동(아들), 이서연 -> 박순자(딸), 김기훈 -> 박순자(친척)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO guardian_elder (id, user_id, elder_id, relationship) VALUES
  (1, 1, 1, 'son'),
  (2, 2, 2, 'daughter'),
  (3, 1, 2, 'relative');

-- ---------------------------------------------------------------------
-- elder_disease : 질병 실데이터
-- ---------------------------------------------------------------------
INSERT IGNORE INTO elder_disease (id, elder_id, disease_name, icd_code, diagnosed_at, status, notes) VALUES
  (1, 1, '본태성 고혈압', 'I10', '2023-05-10', 'managed', '아침 혈압 다소 높음'),
  (2, 1, '제2형 당뇨병', 'E11', '2022-11-02', 'active', '식후 혈당 관리 필요'),
  (3, 2, '골다공증',     'M81', '2024-01-15', 'active', NULL);

-- ---------------------------------------------------------------------
-- elder_medication : 복약 실데이터
-- ---------------------------------------------------------------------
INSERT IGNORE INTO elder_medication
  (id, elder_id, medication_name, atc_code, dosage, interval_hours, start_date, end_date, status) VALUES
  (1, 1, '암로디핀', 'C08CA01', '5mg 1정',  24, '2023-05-10', NULL, 'active'),
  (2, 1, '메트포르민','A10BA02', '500mg 1정', 12, '2022-11-02', NULL, 'active'),
  (3, 2, '알렌드로네이트','M05BA04', '70mg 1정', 168, '2024-01-15', NULL, 'active');

-- ---------------------------------------------------------------------
-- elder_health_note : 시니어별 마크다운 건강 컨텍스트 (시니어당 1개)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO elder_health_note (id, elder_id, content_md) VALUES
  (1, 1, '## 최근 상태\n- 혈압 안정적으로 관리 중\n- 식후 혈당 편차 있음\n- 복약 순응도 양호'),
  (2, 2, '## 최근 상태\n- 골밀도 검사 예정\n- 낙상 주의 필요');

-- ---------------------------------------------------------------------
-- agent_conversation : 에이전트 대화 로그 (transcript = JSON)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO agent_conversation (id, elder_id, purpose, transcript) VALUES
  (1, 1, 'daily_checkin',
   '[{"role":"agent","text":"홍길동님, 오늘 혈압약 드셨어요?"},{"role":"elder","answer":"yes"}]'),
  (2, 2, 'daily_checkin',
   '[{"role":"agent","text":"박순자님, 오늘 물 충분히 드셨어요?"},{"role":"elder","answer":"no"}]');

-- ---------------------------------------------------------------------
-- reminder_rule_master : 사전 정의 리마인드 규칙
-- ---------------------------------------------------------------------
INSERT IGNORE INTO reminder_rule_master
  (id, rule_code, rule_type, match_target, match_code, frequency_type, frequency_value, message_template, expected_response, is_active) VALUES
  (1, 'HTN_MED_CHECK', 'medication', 'medication', 'C08CA01', 'daily',          '09:00',       '{name}님, 혈압약 드셨어요?', 'yes_no', 1),
  (2, 'DM_MED_CHECK',  'medication', 'medication', 'A10BA02', 'interval_hours',  '12',          '{name}님, 당뇨약 드셨어요?', 'yes_no', 1),
  (3, 'HYDRATION_ALL', 'hydration',  'all',        NULL,      'daily',           '10:00,15:00', '{name}님, 물 한 잔 드셨어요?', 'yes_no', 1),
  (4, 'DAILY_MEAL',    'meal',       'all',        NULL,      'daily',           '08:00,12:00,18:00', '{name}님, 식사 하셨어요?', 'yes_no', 1);
