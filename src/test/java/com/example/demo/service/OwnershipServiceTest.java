package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.Elder;
import com.example.demo.repository.ElderRepository;
import com.example.demo.repository.GuardianElderRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OwnershipService 단위 테스트. 보호자-어르신 연결 검증이 유일한 책임이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OwnershipService")
class OwnershipServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ELDER_ID = 1L;

    @Mock
    private ElderRepository elderRepository;
    @Mock
    private GuardianElderRepository guardianElderRepository;

    @InjectMocks
    private OwnershipService ownershipService;

    @Nested
    @DisplayName("verifyAndGetElder")
    class VerifyAndGetElder {

        @Test
        @DisplayName("어르신이 없으면 NOT_FOUND 이다")
        void should_throw_not_found_when_elder_does_not_exist() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("어르신이 없으면 보호관계를 조회하지 않는다")
        void should_not_check_guardianship_when_elder_does_not_exist() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.empty());

            // Act
            assertThatThrownBy(() -> ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(guardianElderRepository, never()).existsByUserIdAndElderId(USER_ID, ELDER_ID);
        }

        @Test
        @DisplayName("보호관계가 없으면 FORBIDDEN 이다")
        void should_throw_forbidden_when_guardianship_is_absent() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.of(Fixtures.elder(ELDER_ID, "김순자")));
            when(guardianElderRepository.existsByUserIdAndElderId(USER_ID, ELDER_ID)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> ownershipService.verifyAndGetElder(USER_ID, ELDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("보호관계가 있으면 어르신을 반환한다")
        void should_return_elder_when_guardianship_exists() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.of(Fixtures.elder(ELDER_ID, "김순자")));
            when(guardianElderRepository.existsByUserIdAndElderId(USER_ID, ELDER_ID)).thenReturn(true);

            // Act
            Elder result = ownershipService.verifyAndGetElder(USER_ID, ELDER_ID);

            // Assert
            assertThat(result.getId()).isEqualTo(ELDER_ID);
            assertThat(result.getName()).isEqualTo("김순자");
        }
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("보호관계가 있으면 예외 없이 통과한다")
        void should_pass_when_guardianship_exists() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.of(Fixtures.elder(ELDER_ID, "김순자")));
            when(guardianElderRepository.existsByUserIdAndElderId(USER_ID, ELDER_ID)).thenReturn(true);

            // Act & Assert
            assertThatCode(() -> ownershipService.verify(USER_ID, ELDER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("보호관계가 없으면 FORBIDDEN 이다")
        void should_throw_forbidden_when_guardianship_is_absent() {
            // Arrange
            when(elderRepository.findById(ELDER_ID)).thenReturn(Optional.of(Fixtures.elder(ELDER_ID, "김순자")));
            when(guardianElderRepository.existsByUserIdAndElderId(USER_ID, ELDER_ID)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> ownershipService.verify(USER_ID, ELDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }
}
