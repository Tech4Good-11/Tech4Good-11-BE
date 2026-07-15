package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.ErrorCode;
import com.example.demo.domain.User;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.SignupRequest;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 단위 테스트. PasswordEncoder 는 모킹해 해싱 구현과 테스트를 분리한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup")
    class Signup {

        @ParameterizedTest(name = "email={0}, password={1}, name={2}")
        @CsvSource(nullValues = "null", value = {
                "null,        pw1234, 홍길동",
                "'',          pw1234, 홍길동",
                "'   ',       pw1234, 홍길동",
                "a@b.com,     null,   홍길동",
                "a@b.com,     '',     홍길동",
                "a@b.com,     '   ',  홍길동",
                "a@b.com,     pw1234, null",
                "a@b.com,     pw1234, ''",
                "a@b.com,     pw1234, '   '"
        })
        @DisplayName("이메일·비밀번호·이름 중 하나라도 비면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_required_field_is_blank(String email, String password, String name) {
            // Arrange
            SignupRequest request = new SignupRequest(email, password, name, "010-1111-2222");

            // Act & Assert
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 INVALID_INPUT 이다")
        void should_throw_invalid_input_when_email_is_already_registered() {
            // Arrange
            when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
            SignupRequest request = new SignupRequest("a@b.com", "pw1234", "홍길동", null);

            // Act & Assert
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                        assertThat(e).hasMessage("이미 가입된 이메일입니다.");
                    });
        }

        @Test
        @DisplayName("중복 이메일이면 저장하지 않는다")
        void should_not_save_when_email_is_duplicated() {
            // Arrange
            when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
            SignupRequest request = new SignupRequest("a@b.com", "pw1234", "홍길동", null);

            // Act
            assertThatThrownBy(() -> authService.signup(request)).isInstanceOf(BusinessException.class);

            // Assert
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("비밀번호를 평문이 아닌 해시로 저장한다")
        void should_store_hashed_password_when_signup_succeeds() {
            // Arrange
            when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
            when(passwordEncoder.encode("pw1234")).thenReturn("HASHED");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            authService.signup(new SignupRequest("a@b.com", "pw1234", "홍길동", "010-1111-2222"));

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
        }

        @Test
        @DisplayName("가입에 성공하면 비밀번호가 없는 응답을 반환한다")
        void should_return_user_response_when_signup_succeeds() {
            // Arrange
            when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
            when(passwordEncoder.encode("pw1234")).thenReturn("HASHED");
            when(userRepository.save(any(User.class)))
                    .thenReturn(Fixtures.user(5L, "a@b.com", "HASHED"));

            // Act
            UserResponse result = authService.signup(new SignupRequest("a@b.com", "pw1234", "홍길동", null));

            // Assert
            assertThat(result.id()).isEqualTo(5L);
            assertThat(result.email()).isEqualTo("a@b.com");
        }
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("이메일이 없으면 UNAUTHORIZED 이다")
        void should_throw_unauthorized_when_email_is_not_registered() {
            // Arrange
            when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.authenticate(new LoginRequest("nobody@b.com", "pw1234")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 UNAUTHORIZED 이다")
        void should_throw_unauthorized_when_password_does_not_match() {
            // Arrange
            when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));
            when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.authenticate(new LoginRequest("a@b.com", "wrong")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("이메일 존재 여부를 노출하지 않도록 동일한 메시지를 사용한다")
        void should_use_same_message_for_unknown_email_and_wrong_password() {
            // Arrange
            when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));
            when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

            // Act
            Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(
                    () -> authService.authenticate(new LoginRequest("nobody@b.com", "pw1234")));
            Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                    () -> authService.authenticate(new LoginRequest("a@b.com", "wrong")));

            // Assert
            assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
        }

        @Test
        @DisplayName("비밀번호가 null 이면 UNAUTHORIZED 이다")
        void should_throw_unauthorized_when_password_is_null() {
            // Arrange
            when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));

            // Act & Assert
            assertThatThrownBy(() -> authService.authenticate(new LoginRequest("a@b.com", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("비밀번호가 null 이면 인코더를 호출하지 않는다")
        void should_not_call_encoder_when_password_is_null() {
            // Arrange
            when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));

            // Act
            assertThatThrownBy(() -> authService.authenticate(new LoginRequest("a@b.com", null)))
                    .isInstanceOf(BusinessException.class);

            // Assert
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("이메일이 null 이면 빈 문자열로 조회해 UNAUTHORIZED 이다")
        void should_throw_unauthorized_when_email_is_null() {
            // Arrange
            when(userRepository.findByEmail("")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.authenticate(new LoginRequest(null, "pw1234")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("이메일과 비밀번호가 맞으면 사용자를 반환한다")
        void should_return_user_when_credentials_are_valid() {
            // Arrange
            when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));
            when(passwordEncoder.matches("pw1234", "HASHED")).thenReturn(true);

            // Act
            User result = authService.authenticate(new LoginRequest("a@b.com", "pw1234"));

            // Assert
            assertThat(result.getId()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("사용자가 없으면 UNAUTHORIZED 이다")
        void should_throw_unauthorized_when_user_does_not_exist() {
            // Arrange
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.getById(5L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("사용자가 있으면 응답 DTO 로 반환한다")
        void should_return_user_response_when_user_exists() {
            // Arrange
            when(userRepository.findById(5L)).thenReturn(Optional.of(Fixtures.user(5L, "a@b.com", "HASHED")));

            // Act
            UserResponse result = authService.getById(5L);

            // Assert
            assertThat(result.email()).isEqualTo("a@b.com");
        }
    }
}
