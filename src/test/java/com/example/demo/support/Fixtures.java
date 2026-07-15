package com.example.demo.support;

import com.example.demo.domain.AgentConversation;
import com.example.demo.domain.Elder;
import com.example.demo.domain.ElderDailyLog;
import com.example.demo.domain.ElderDisease;
import com.example.demo.domain.ElderHealthNote;
import com.example.demo.domain.ElderMedication;
import com.example.demo.domain.ElderMedicationIntake;
import com.example.demo.domain.ReminderRuleMaster;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.domain.enums.DiseaseStatus;
import com.example.demo.domain.enums.ExpectedResponse;
import com.example.demo.domain.enums.FrequencyType;
import com.example.demo.domain.enums.Gender;
import com.example.demo.domain.enums.MatchTarget;
import com.example.demo.domain.enums.MedicationStatus;
import com.example.demo.domain.enums.RuleType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테스트용 엔티티 팩토리.
 *
 * <p>엔티티들은 id 가 DB 생성(@GeneratedValue)이라 세터가 없고, ReminderRuleMaster 는
 * 읽기 전용이라 빌더조차 없다. 단위 테스트에서 값을 채우기 위해 리플렉션을 사용한다.
 * 프로덕션 코드에 테스트 전용 세터를 추가하지 않기 위한 의도적 선택이다.
 */
public final class Fixtures {

    private Fixtures() {
    }

    // ---------- 리플렉션 헬퍼 ----------

    /** 상속 계층을 따라 올라가며 필드를 찾아 값을 주입한다. */
    public static <T> T setField(T target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return target;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("필드 주입 실패: " + fieldName, e);
            }
        }
        throw new IllegalArgumentException("필드를 찾을 수 없음: " + fieldName);
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("인스턴스 생성 실패: " + type, e);
        }
    }

    // ---------- Elder ----------

    public static Elder elder(Long id, String name) {
        Elder elder = Elder.builder()
                .name(name)
                .birthDate(LocalDate.of(1945, 3, 1))
                .gender(Gender.F)
                .phone("010-0000-0000")
                .build();
        return setField(elder, "id", id);
    }

    // ---------- User ----------

    public static User user(Long id, String email, String passwordHash) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .name("보호자")
                .phone("010-1111-2222")
                .build();
        return setField(user, "id", id);
    }

    // ---------- ElderDisease ----------

    public static ElderDisease disease(Long id, Long elderId, String name, String icdCode, DiseaseStatus status) {
        ElderDisease disease = ElderDisease.builder()
                .elderId(elderId)
                .diseaseName(name)
                .icdCode(icdCode)
                .diagnosedAt(LocalDate.of(2020, 1, 1))
                .status(status)
                .notes(null)
                .build();
        return setField(disease, "id", id);
    }

    // ---------- ElderMedication ----------

    public static ElderMedication medication(Long id, Long elderId, String name, String atcCode) {
        return medication(id, elderId, name, atcCode, "1정", MedicationStatus.active);
    }

    public static ElderMedication medication(Long id, Long elderId, String name, String atcCode,
                                             String dosage, MedicationStatus status) {
        ElderMedication medication = ElderMedication.builder()
                .elderId(elderId)
                .medicationName(name)
                .atcCode(atcCode)
                .dosage(dosage)
                .intervalHours(24)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(null)
                .status(status)
                .build();
        return setField(medication, "id", id);
    }

    // ---------- ElderMedicationIntake ----------

    public static ElderMedicationIntake intake(Long id, Long elderId, Long medicationId,
                                               LocalDate date, Boolean taken) {
        ElderMedicationIntake intake = ElderMedicationIntake.builder()
                .elderId(elderId)
                .medicationId(medicationId)
                .intakeDate(date)
                .taken(taken)
                .sourceConversationId(null)
                .build();
        return setField(intake, "id", id);
    }

    // ---------- ElderDailyLog ----------

    public static ElderDailyLog dailyLog(Long id, Long elderId, LocalDate date,
                                         BigDecimal sleepHours, Integer exerciseMinutes,
                                         String summary, String checklistJson) {
        ElderDailyLog log = ElderDailyLog.builder()
                .elderId(elderId)
                .logDate(date)
                .sleepHours(sleepHours)
                .exerciseMinutes(exerciseMinutes)
                .conditionSummary(summary)
                .checklistAnswers(checklistJson)
                .sourceConversationId(null)
                .build();
        return setField(log, "id", id);
    }

    // ---------- ElderHealthNote ----------

    public static ElderHealthNote healthNote(Long id, Long elderId, String contentMd) {
        ElderHealthNote note = ElderHealthNote.builder()
                .elderId(elderId)
                .contentMd(contentMd)
                .build();
        setField(note, "id", id);
        return setField(note, "updatedAt", LocalDateTime.of(2026, 7, 16, 9, 0));
    }

    // ---------- AgentConversation ----------

    public static AgentConversation conversation(Long id, Long elderId, ConversationPurpose purpose,
                                                 String transcript) {
        AgentConversation conv = AgentConversation.builder()
                .elderId(elderId)
                .purpose(purpose)
                .transcript(transcript)
                .build();
        setField(conv, "id", id);
        return setField(conv, "createdAt", LocalDateTime.of(2026, 7, 16, 10, 0));
    }

    // ---------- ReminderRuleMaster ----------

    /** 읽기 전용 엔티티라 빌더가 없어 리플렉션으로 조립한다. */
    public static ReminderRuleMaster rule(String ruleCode, RuleType ruleType, MatchTarget matchTarget,
                                          String matchCode, FrequencyType frequencyType, String frequencyValue,
                                          String messageTemplate, ExpectedResponse expectedResponse) {
        ReminderRuleMaster rule = newInstance(ReminderRuleMaster.class);
        setField(rule, "id", Math.abs(ruleCode.hashCode() % 10_000));
        setField(rule, "ruleCode", ruleCode);
        setField(rule, "ruleType", ruleType);
        setField(rule, "matchTarget", matchTarget);
        setField(rule, "matchCode", matchCode);
        setField(rule, "frequencyType", frequencyType);
        setField(rule, "frequencyValue", frequencyValue);
        setField(rule, "messageTemplate", messageTemplate);
        setField(rule, "expectedResponse", expectedResponse);
        setField(rule, "isActive", Boolean.TRUE);
        return rule;
    }
}
