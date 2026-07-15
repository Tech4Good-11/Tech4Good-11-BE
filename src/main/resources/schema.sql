-- =====================================================================
--  시니어 건강관리 앱 - MySQL 8.0 스키마 (MVP)  utf8mb4 / InnoDB
--  자녀(User)가 에이전트를 통해 시니어(Elder)의 건강을 관리하는 서비스
--  테이블 8개: users, elders, guardian_elder, agent_conversation,
--             elder_health_note, elder_disease, elder_medication,
--             reminder_rule_master
--
--  * spring.sql.init 으로 앱 시작 시마다 실행되지만,
--    CREATE TABLE IF NOT EXISTS 라서 "처음에만" 테이블을 만들고
--    이후 재부팅에서는 기존 테이블/데이터를 그대로 유지한다.
--  * 접속 대상 DB(compose.yaml 의 mydatabase)에 그대로 생성하므로
--    CREATE DATABASE / USE 구문은 사용하지 않는다.
-- =====================================================================


-- =====================================================================
--  [ RESET / 초기화 블록 ]  ── 로컬 테스트 데이터를 전부 날리고 싶을 때만 사용
-- ---------------------------------------------------------------------
--  사용법: 아래 "/*" 한 줄과 맨 끝 "*/" 한 줄을 지운(주석 해제) 뒤 앱을 재시작.
--          -> 모든 테이블이 DROP 되고 아래 CREATE 로 새로 만들어지며
--             data.sql 의 시드 데이터가 다시 삽입된다.
--          -> 초기화가 끝나면 다시 "/*" 와 "*/" 를 되살려(주석 처리) 두어야
--             다음 재부팅부터 데이터가 보존된다.
-- =====================================================================
/*
DROP TABLE IF EXISTS elder_medication_intake;
DROP TABLE IF EXISTS elder_daily_log;
DROP TABLE IF EXISTS guardian_elder;
DROP TABLE IF EXISTS agent_conversation;
DROP TABLE IF EXISTS elder_health_note;
DROP TABLE IF EXISTS elder_disease;
DROP TABLE IF EXISTS elder_medication;
DROP TABLE IF EXISTS reminder_rule_master;
DROP TABLE IF EXISTS elders;
DROP TABLE IF EXISTS users;
*/


-- =====================================================================
-- 1. users : 자녀(보호자) = 로그인 주체
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK, 자녀(보호자) 식별자',
  email         VARCHAR(255)    NOT NULL                COMMENT '로그인 이메일(고유). 예: parent@example.com',
  password_hash VARCHAR(255)    NOT NULL                COMMENT '비밀번호 해시(bcrypt/argon2). 평문 저장 금지',
  name          VARCHAR(100)    NOT NULL                COMMENT '보호자 이름. 예: 김기훈',
  phone         VARCHAR(30)     NULL                    COMMENT '연락처(선택). 예: 010-1234-5678',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시각',
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB COMMENT='자녀(보호자) 계정';

-- =====================================================================
-- 2. elders : 시니어(어르신) = 관리 대상
--    자체 로그인 없이 보호자가 계정을 소유한다는 가정
-- =====================================================================
CREATE TABLE IF NOT EXISTS elders (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK, 시니어 식별자',
  name        VARCHAR(100)    NOT NULL                COMMENT '시니어 이름. 예: 홍길동',
  birth_date  DATE            NULL                    COMMENT '생년월일(연령/알림 문구용). 예: 1945-03-11',
  gender      ENUM('M','F','other') NULL              COMMENT '성별. M=남성, F=여성, other=기타/미상',
  phone       VARCHAR(30)     NULL                    COMMENT '시니어 본인 연락처(선택)',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시각',
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='시니어(관리 대상)';

-- =====================================================================
-- 3. guardian_elder : 자녀 <-> 시니어 M:N 보호관계
-- =====================================================================
CREATE TABLE IF NOT EXISTS guardian_elder (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  user_id      BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> users.id (자녀)',
  elder_id     BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id (시니어)',
  relationship ENUM('son','daughter','spouse','sibling','relative','caregiver','other')
               NOT NULL DEFAULT 'other'
               COMMENT '관계. son=아들, daughter=딸, spouse=배우자, sibling=형제자매, relative=친척, caregiver=간병인, other=기타',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '관계 등록 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_guardian_elder (user_id, elder_id),   -- 동일 (자녀,시니어) 중복 관계 방지
  KEY idx_ge_elder (elder_id),
  CONSTRAINT fk_ge_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
  CONSTRAINT fk_ge_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='자녀-시니어 다대다 보호관계';

-- =====================================================================
-- 4. agent_conversation : 에이전트 대화 로그(blob 저장)
--    진단서/처방전 사진도 이 대화의 첨부로 처리하고, 결과만 실데이터로 저장
-- =====================================================================
CREATE TABLE IF NOT EXISTS agent_conversation (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK, 대화 세션 식별자',
  elder_id    BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id (누구의 대화인지)',
  purpose     ENUM('daily_checkin','document_intake','free')
              NOT NULL DEFAULT 'daily_checkin'
              COMMENT '대화 목적. daily_checkin=일일 Yes/No 안부문진, document_intake=진단서/처방전 처리, free=자유대화',
  transcript  JSON            NOT NULL                COMMENT '대화 전체 blob. 예: [{"role":"agent","text":"약 드셨어요?"},{"role":"elder","answer":"yes"}]',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '대화 시각',
  PRIMARY KEY (id),
  KEY idx_conv_elder_time (elder_id, created_at),
  CONSTRAINT fk_conv_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='에이전트 대화 기록(blob)';

-- =====================================================================
-- 5. elder_health_note : 시니어별 마크다운 건강 컨텍스트 (LLM 갱신, 1:1)
--    대시보드는 이 컨텍스트 + 질병/복약 실데이터를 참조해 구성
-- =====================================================================
CREATE TABLE IF NOT EXISTS elder_health_note (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  elder_id    BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id (시니어당 1개)',
  content_md  MEDIUMTEXT      NOT NULL                COMMENT '건강정보 마크다운 컨텍스트. 예: "## 최근 상태\n- 혈압 안정..."',
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'LLM 마지막 갱신 시각',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_note_elder (elder_id),   -- 시니어당 마크다운 1개 보장
  CONSTRAINT fk_note_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='시니어별 마크다운 건강 컨텍스트';

-- =====================================================================
-- 6. elder_disease : 시니어 질병 실데이터 (진단서 처리 결과)
--    표준 코드(ICD)를 직접 보관 -> 마스터 테이블 없이 규칙 매칭
-- =====================================================================
CREATE TABLE IF NOT EXISTS elder_disease (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  elder_id      BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id',
  disease_name  VARCHAR(200)    NOT NULL                COMMENT '질병명. 예: 고혈압, HIV 감염',
  icd_code      VARCHAR(20)     NULL                    COMMENT '규칙 매칭 키(ICD-10/11). 예: I10, B20',
  diagnosed_at  DATE            NULL                    COMMENT '진단일. 예: 2024-06-01',
  status        ENUM('active','managed','resolved')
                NOT NULL DEFAULT 'active'
                COMMENT '상태. active=치료중, managed=관리중(안정), resolved=완치/종료',
  notes         VARCHAR(500)    NULL                    COMMENT '비고(특이사항)',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_ed_elder (elder_id, status),
  KEY idx_ed_icd (icd_code),
  CONSTRAINT fk_ed_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='시니어 질병 실데이터';

-- =====================================================================
-- 7. elder_medication : 시니어 복약 실데이터 (처방전 처리 결과)
-- =====================================================================
CREATE TABLE IF NOT EXISTS elder_medication (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  elder_id         BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id',
  medication_name  VARCHAR(200)    NOT NULL                COMMENT '약품명. 예: 암로디핀, 아빌리파이',
  atc_code         VARCHAR(20)     NULL                    COMMENT '규칙 매칭 키(ATC). 예: C08CA01',
  dosage           VARCHAR(100)    NULL                    COMMENT '용량/1회분. 예: 1정, 5mg',
  interval_hours   INT UNSIGNED    NULL                    COMMENT '복용 간격(시간). 예: 8=8시간마다, 24=1일1회',
  start_date       DATE            NULL                    COMMENT '복용 시작일',
  end_date         DATE            NULL                    COMMENT '복용 종료일(무기한이면 NULL)',
  status           ENUM('active','stopped','completed')
                   NOT NULL DEFAULT 'active'
                   COMMENT '상태. active=복용중, stopped=중단, completed=처방완료',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_em_elder (elder_id, status),
  KEY idx_em_atc (atc_code),
  CONSTRAINT fk_em_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='시니어 복약 실데이터';

-- =====================================================================
-- 8. reminder_rule_master : 사전 정의된 리마인드 규칙 (리마인드는 이 테이블만)
--    복약/질병 실데이터를 규칙과 매칭해 런타임에 알림 발송
-- =====================================================================
CREATE TABLE IF NOT EXISTS reminder_rule_master (
  id               INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  rule_code        VARCHAR(50)  NOT NULL                COMMENT '규칙 식별 코드(고유). 예: HIV_MED_CHECK',
  rule_type        ENUM('medication','hydration','meal','vital_check','custom')
                   NOT NULL
                   COMMENT '규칙 종류. medication=복약알림, hydration=수분섭취, meal=식사, vital_check=활력징후확인, custom=기타',
  match_target     ENUM('disease','medication','all')
                   NOT NULL
                   COMMENT '매칭 대상. disease=질병실데이터, medication=복약실데이터, all=대상전체',
  match_code       VARCHAR(20)  NULL
                   COMMENT '매칭 키(elder_disease.icd_code 또는 elder_medication.atc_code). NULL=해당 target 전체 적용. 예: B20',
  frequency_type   ENUM('interval_hours','daily','weekly')
                   NOT NULL
                   COMMENT '주기 유형. interval_hours=N시간마다, daily=매일 지정시각, weekly=매주',
  frequency_value  VARCHAR(100) NOT NULL
                   COMMENT '주기 값. interval_hours이면 "8", daily이면 "09:00,21:00", weekly이면 "MON 09:00"',
  message_template VARCHAR(500) NOT NULL
                   COMMENT '알림 문구 템플릿. 예: "{name}님, 약 드셨어요?" / "{name}님, 물 드셨어요?"',
  expected_response ENUM('yes_no','none')
                   NOT NULL DEFAULT 'yes_no'
                   COMMENT '기대 응답. yes_no=예/아니오 응답 수집, none=단순 안내(응답 불필요)',
  is_active        TINYINT(1)   NOT NULL DEFAULT 1
                   COMMENT '활성 여부. 1=사용, 0=비활성',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '규칙 생성 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_rule_code (rule_code),
  KEY idx_rule_match (match_target, match_code, is_active)
) ENGINE=InnoDB COMMENT='사전 정의 리마인드 규칙';

-- =====================================================================
-- 9. elder_daily_log : 하루치 생활 로그 (대화에서 추출 / 수동 입력)
--    대시보드의 "수면시간 / 운동량 / AI 상담 요약 / 체크리스트" 원천.
--    * 대화로 알아낼 수 있는 값만 보관한다.
--      - sleep_hours     : "몇 시간 주무셨어요?" -> 자가보고 가능
--      - exercise_minutes: "산책 얼마나 하셨어요?" -> 자가보고(분) 가능.
--                          걸음수는 대화로 알 수 없으므로 보관하지 않는다(웨어러블 필요).
--    * 어르신 1명당 하루 1행(UNIQUE elder_id + log_date).
-- =====================================================================
CREATE TABLE IF NOT EXISTS elder_daily_log (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  elder_id               BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id',
  log_date               DATE            NOT NULL                COMMENT '기준 날짜(하루 1행). 예: 2026-07-16',
  sleep_hours            DECIMAL(3,1)    NULL                    COMMENT '수면시간(시간, 대화 자가보고). 예: 6.5. 모르면 NULL',
  exercise_minutes       INT UNSIGNED    NULL                    COMMENT '운동량(분, 대화 자가보고). 예: 30. 걸음수 아님. 모르면 NULL',
  condition_summary      VARCHAR(500)    NULL                    COMMENT 'AI 상담 요약(대화 한 줄 요약). 예: "혈압약 복용, 산책 30분"',
  checklist_answers      JSON            NULL                    COMMENT '체크리스트 응답. 예: {"HTN_MED_CHECK":"yes","HYDRATION_ALL":"no"}',
  source_conversation_id BIGINT UNSIGNED NULL                    COMMENT '이 값을 채운 대화 id(추적용)',
  created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_daily_log (elder_id, log_date),   -- 어르신당 하루 1행 보장
  KEY idx_dl_elder_date (elder_id, log_date),
  CONSTRAINT fk_dl_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='어르신 하루 생활 로그(대화 추출/수동 입력)';

-- =====================================================================
-- 10. elder_medication_intake : 복약 여부(일자별)
--     대시보드의 "오늘치 복용한 약" 원천.
--     대화("약 드셨어요?") 또는 체크리스트 체크로 채워진다.
--     약 1개당 하루 1행(UNIQUE medication_id + intake_date).
-- =====================================================================
CREATE TABLE IF NOT EXISTS elder_medication_intake (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
  elder_id               BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elders.id (조회 편의용 비정규화)',
  medication_id          BIGINT UNSIGNED NOT NULL                COMMENT 'FK -> elder_medication.id',
  intake_date            DATE            NOT NULL                COMMENT '복용 기준일. 예: 2026-07-16',
  taken                  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '복용 여부. 1=복용함, 0=미복용',
  source_conversation_id BIGINT UNSIGNED NULL                    COMMENT '이 값을 채운 대화 id(추적용). 수동 체크면 NULL',
  created_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_intake (medication_id, intake_date),  -- 약당 하루 1행 보장
  KEY idx_mi_elder_date (elder_id, intake_date),
  CONSTRAINT fk_mi_elder FOREIGN KEY (elder_id) REFERENCES elders(id) ON DELETE CASCADE,
  CONSTRAINT fk_mi_med   FOREIGN KEY (medication_id) REFERENCES elder_medication(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='어르신 복약 여부(일자별)';
