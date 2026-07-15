package com.example.demo.service;

import com.example.demo.domain.enums.ConversationPurpose;
import com.example.demo.dto.dailylog.DailyLogResponse;
import com.example.demo.dto.dailylog.MedicationIntakeResponse;
import com.example.demo.dto.dashboard.DashboardResponse;
import com.example.demo.repository.AgentConversationRepository;
import com.example.demo.repository.ElderDiseaseRepository;
import com.example.demo.repository.ElderHealthNoteRepository;
import com.example.demo.repository.ElderMedicationRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * DashboardService 단위 테스트.
 *
 * <p>건강점수 계산이 private 이므로 getDashboard() 결과의 healthScore 로 검증한다.
 * 근거가 없는 항목은 null 로 남기고 평균에서 제외하는 규칙이 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;

    @Mock
    private OwnershipService ownershipService;
    @Mock
    private ElderHealthNoteRepository healthNoteRepository;
    @Mock
    private ElderDiseaseRepository diseaseRepository;
    @Mock
    private ElderMedicationRepository medicationRepository;
    @Mock
    private AgentConversationRepository conversationRepository;
    @Mock
    private ReminderService reminderService;
    @Mock
    private DailyLogService dailyLogService;

    @InjectMocks
    private DashboardService dashboardService;

    /**
     * 대부분의 협력자는 Mockito 기본값(빈 List / Optional.empty)으로 충분하다.
     * 어르신 조회만 항상 필요하므로 여기서 스텁한다.
     */
    private void givenElder() {
        when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID)).thenReturn(Fixtures.elder(ELDER_ID, "김순자"));
    }

    private void givenDailyLog(BigDecimal sleepHours, Integer exerciseMinutes) {
        when(dailyLogService.readLog(anyLong(), any(LocalDate.class))).thenReturn(new DailyLogResponse(
                ELDER_ID, LocalDate.now(), sleepHours, exerciseMinutes, null, List.of(), null, null));
    }

    private void givenTodayMedications(MedicationIntakeResponse... intakes) {
        when(dailyLogService.readMedicationIntakes(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(intakes));
    }

    private static MedicationIntakeResponse intake(Long id, String name, Boolean taken) {
        return new MedicationIntakeResponse(id, name, "1정", taken, LocalDate.now());
    }

    private DashboardResponse.HealthScore healthScore() {
        return dashboardService.getDashboard(USER_ID, ELDER_ID).healthScore();
    }

    @Nested
    @DisplayName("복약 점수")
    class MedicationScore {

        @Test
        @DisplayName("복용 여부가 전부 미확인이면 null 이다")
        void should_be_null_when_all_taken_are_unknown() {
            // Arrange
            givenElder();
            givenTodayMedications(intake(1L, "아모디핀", null), intake(2L, "메트포르민", null));

            // Act
            DashboardResponse.HealthScore score = healthScore();

            // Assert
            assertThat(score.medicationScore()).isNull();
        }

        @Test
        @DisplayName("확인된 약이 모두 복용됐으면 100 이다")
        void should_be_100_when_all_known_medications_are_taken() {
            // Arrange
            givenElder();
            givenTodayMedications(intake(1L, "아모디핀", true), intake(2L, "메트포르민", true));

            // Act & Assert
            assertThat(healthScore().medicationScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("확인된 약을 하나도 안 먹었으면 0 이다")
        void should_be_0_when_no_known_medication_is_taken() {
            // Arrange
            givenElder();
            givenTodayMedications(intake(1L, "아모디핀", false));

            // Act & Assert
            assertThat(healthScore().medicationScore()).isZero();
        }

        @Test
        @DisplayName("미확인(null) 약은 분모에서 제외한다")
        void should_exclude_unknown_medications_from_ratio() {
            // Arrange: 확인된 약 2개 중 1개 복용, 나머지 1개는 미확인
            givenElder();
            givenTodayMedications(
                    intake(1L, "아모디핀", true),
                    intake(2L, "메트포르민", false),
                    intake(3L, "아스피린", null));

            // Act & Assert: 3개 중 1개(33)가 아니라 2개 중 1개(50)
            assertThat(healthScore().medicationScore()).isEqualTo(50);
        }

        @Test
        @DisplayName("활성 약이 없으면 null 이다")
        void should_be_null_when_there_are_no_medications() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThat(healthScore().medicationScore()).isNull();
        }
    }

    @Nested
    @DisplayName("수면 점수")
    class SleepScore {

        @ParameterizedTest(name = "수면 {0}시간 -> {1}점")
        @CsvSource({
                "7.0, 100",   // 기준
                "6.0, 75",    // 1시간 차이 -25
                "8.0, 75",    // 초과도 동일하게 감점
                "5.0, 50",
                "3.0, 0",     // 4시간 차이 -> 0
                "0.0, 0",     // 하한 0 (음수 아님)
                "15.0, 0"     // 과다 수면도 하한 0
        })
        @DisplayName("7시간을 100점 기준으로 1시간당 25점 감점하고 0에서 멈춘다")
        void should_score_sleep_by_distance_from_seven_hours(String hours, int expected) {
            // Arrange
            givenElder();
            givenDailyLog(new BigDecimal(hours), null);

            // Act & Assert
            assertThat(healthScore().sleepScore()).isEqualTo(expected);
        }

        @Test
        @DisplayName("수면 기록이 없으면 null 이다")
        void should_be_null_when_sleep_hours_is_absent() {
            // Arrange
            givenElder();
            givenDailyLog(null, null);

            // Act & Assert
            assertThat(healthScore().sleepScore()).isNull();
        }

        @Test
        @DisplayName("30분 단위 수면도 비례 감점한다")
        void should_score_half_hour_precision() {
            // Arrange: 6.5시간 -> 0.5 차이 -> 100 - 12.5 = 87.5 -> 반올림 88
            givenElder();
            givenDailyLog(new BigDecimal("6.5"), null);

            // Act & Assert
            assertThat(healthScore().sleepScore()).isEqualTo(88);
        }
    }

    @Nested
    @DisplayName("운동 점수")
    class ExerciseScore {

        @ParameterizedTest(name = "운동 {0}분 -> {1}점")
        @CsvSource({
                "30, 100",   // 기준
                "15, 50",    // 비례 배분
                "0, 0",
                "60, 100",   // 상한 100
                "300, 100"   // 초과해도 100
        })
        @DisplayName("30분을 100점 기준으로 비례 배분하되 100을 넘지 않는다")
        void should_score_exercise_proportionally_capped_at_100(int minutes, int expected) {
            // Arrange
            givenElder();
            givenDailyLog(null, minutes);

            // Act & Assert
            assertThat(healthScore().exerciseScore()).isEqualTo(expected);
        }

        @Test
        @DisplayName("운동 기록이 없으면 null 이다")
        void should_be_null_when_exercise_minutes_is_absent() {
            // Arrange
            givenElder();
            givenDailyLog(null, null);

            // Act & Assert
            assertThat(healthScore().exerciseScore()).isNull();
        }
    }

    @Nested
    @DisplayName("총점")
    class TotalScore {

        @Test
        @DisplayName("근거가 하나도 없으면 총점은 null 이다")
        void should_be_null_when_all_parts_are_null() {
            // Arrange
            givenElder();

            // Act
            DashboardResponse.HealthScore score = healthScore();

            // Assert
            assertThat(score.score()).isNull();
            assertThat(score.medicationScore()).isNull();
            assertThat(score.sleepScore()).isNull();
            assertThat(score.exerciseScore()).isNull();
        }

        @Test
        @DisplayName("근거가 없으면 기록 없음 안내 문구를 반환한다")
        void should_return_empty_comment_when_score_is_null() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThat(healthScore().comment()).isEqualTo("오늘 기록이 아직 없어요. 대화를 나누면 점수가 계산됩니다.");
        }

        @Test
        @DisplayName("null 항목은 평균에서 제외한다")
        void should_average_only_non_null_parts() {
            // Arrange: 수면 100 + 운동 50 만 존재(복약 없음) -> (100+50)/2 = 75
            givenElder();
            givenDailyLog(new BigDecimal("7.0"), 15);

            // Act
            DashboardResponse.HealthScore score = healthScore();

            // Assert
            assertThat(score.medicationScore()).isNull();
            assertThat(score.score()).isEqualTo(75);
        }

        @Test
        @DisplayName("세 항목이 모두 있으면 셋의 평균이다")
        void should_average_all_three_parts_when_all_present() {
            // Arrange: 복약 100, 수면 100, 운동 100
            givenElder();
            givenDailyLog(new BigDecimal("7.0"), 30);
            givenTodayMedications(intake(1L, "아모디핀", true));

            // Act & Assert
            assertThat(healthScore().score()).isEqualTo(100);
        }

        @Test
        @DisplayName("평균은 반올림한다")
        void should_round_average_to_nearest_integer() {
            // Arrange: 복약 50 + 수면 75 -> 62.5 -> 63
            givenElder();
            givenDailyLog(new BigDecimal("6.0"), null);
            givenTodayMedications(intake(1L, "아모디핀", true), intake(2L, "메트포르민", false));

            // Act & Assert
            assertThat(healthScore().score()).isEqualTo(63);
        }

        @Test
        @DisplayName("80점 이상이면 긍정 코멘트를 반환한다")
        void should_return_positive_comment_when_score_is_at_least_80() {
            // Arrange: 복약 100, 수면 100, 운동 100 -> 모든 항목 존재 -> 누락 표기 없음
            givenElder();
            givenDailyLog(new BigDecimal("7.0"), 30);
            givenTodayMedications(intake(1L, "아모디핀", true));

            // Act & Assert
            assertThat(healthScore().comment()).isEqualTo("오늘 건강 관리가 잘 되고 있어요.");
        }

        @Test
        @DisplayName("50점 이상 80점 미만이면 중간 코멘트를 반환한다")
        void should_return_neutral_comment_when_score_is_between_50_and_80() {
            // Arrange: 수면 75 단독
            givenElder();
            givenDailyLog(new BigDecimal("6.0"), null);

            // Act & Assert
            assertThat(healthScore().comment()).startsWith("대체로 괜찮지만 조금 더 챙기면 좋겠어요.");
        }

        @Test
        @DisplayName("50점 미만이면 주의 코멘트를 반환한다")
        void should_return_warning_comment_when_score_is_below_50() {
            // Arrange: 운동 0 단독
            givenElder();
            givenDailyLog(null, 0);

            // Act & Assert
            assertThat(healthScore().comment()).startsWith("오늘은 관리가 부족했어요.");
        }

        @Test
        @DisplayName("누락된 항목을 코멘트 끝에 표기한다")
        void should_list_missing_parts_in_comment() {
            // Arrange: 수면만 존재 -> 복약·운동 누락
            givenElder();
            givenDailyLog(new BigDecimal("7.0"), null);

            // Act & Assert
            assertThat(healthScore().comment()).isEqualTo("오늘 건강 관리가 잘 되고 있어요. (복약·운동 기록 없음)");
        }
    }

    @Nested
    @DisplayName("대시보드 구성")
    class DashboardComposition {

        @Test
        @DisplayName("어르신 기본 정보를 그대로 싣는다")
        void should_include_elder_brief() {
            // Arrange
            givenElder();

            // Act
            DashboardResponse.ElderBrief brief = dashboardService.getDashboard(USER_ID, ELDER_ID).elder();

            // Assert
            assertThat(brief.id()).isEqualTo(ELDER_ID);
            assertThat(brief.name()).isEqualTo("김순자");
            assertThat(brief.gender()).isEqualTo("F");
        }

        @Test
        @DisplayName("건강 노트가 없으면 null 이다")
        void should_return_null_health_note_when_absent() {
            // Arrange
            givenElder();

            // Act & Assert
            assertThat(dashboardService.getDashboard(USER_ID, ELDER_ID).healthNote()).isNull();
        }

        @Test
        @DisplayName("건강 노트가 있으면 내용을 싣는다")
        void should_include_health_note_when_present() {
            // Arrange
            givenElder();
            when(healthNoteRepository.findByElderId(ELDER_ID))
                    .thenReturn(Optional.of(Fixtures.healthNote(1L, ELDER_ID, "## 최근 상태\n- 양호")));

            // Act
            DashboardResponse.HealthNoteBrief note = dashboardService.getDashboard(USER_ID, ELDER_ID).healthNote();

            // Assert
            assertThat(note.contentMd()).isEqualTo("## 최근 상태\n- 양호");
        }

        @Test
        @DisplayName("document_intake 대화는 최근 체크인 목록에서 제외한다")
        void should_exclude_document_intake_from_recent_checkins() {
            // Arrange
            givenElder();
            when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of(
                    Fixtures.conversation(10L, ELDER_ID, ConversationPurpose.daily_checkin, "[]"),
                    Fixtures.conversation(11L, ELDER_ID, ConversationPurpose.document_intake, "[]"),
                    Fixtures.conversation(12L, ELDER_ID, ConversationPurpose.free, "[]")));

            // Act
            List<DashboardResponse.CheckinBrief> checkins =
                    dashboardService.getDashboard(USER_ID, ELDER_ID).recentCheckins();

            // Assert
            assertThat(checkins).extracting(DashboardResponse.CheckinBrief::conversationId)
                    .containsExactly(10L, 12L);
        }

        @Test
        @DisplayName("오늘 로그의 출처 대화에만 AI 요약을 붙인다")
        void should_attach_summary_only_to_source_conversation() {
            // Arrange: 오늘 로그가 대화 10 에서 추출됨
            givenElder();
            when(dailyLogService.readLog(anyLong(), any(LocalDate.class))).thenReturn(new DailyLogResponse(
                    ELDER_ID, LocalDate.now(), null, null, "컨디션 양호", List.of(), 10L, null));
            when(conversationRepository.findTop5ByElderIdOrderByCreatedAtDesc(ELDER_ID)).thenReturn(List.of(
                    Fixtures.conversation(10L, ELDER_ID, ConversationPurpose.daily_checkin, "[]"),
                    Fixtures.conversation(12L, ELDER_ID, ConversationPurpose.free, "[]")));

            // Act
            List<DashboardResponse.CheckinBrief> checkins =
                    dashboardService.getDashboard(USER_ID, ELDER_ID).recentCheckins();

            // Assert
            assertThat(checkins).extracting(DashboardResponse.CheckinBrief::summary)
                    .containsExactly("컨디션 양호", null);
        }

        @Test
        @DisplayName("소유권 검증에 실패하면 대시보드를 조회하지 않는다")
        void should_propagate_when_ownership_verification_fails() {
            // Arrange
            when(ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .thenThrow(new com.example.demo.common.BusinessException(
                            com.example.demo.common.ErrorCode.FORBIDDEN));

            // Act & Assert
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> dashboardService.getDashboard(USER_ID, ELDER_ID))
                    .isInstanceOf(com.example.demo.common.BusinessException.class);
        }
    }
}
