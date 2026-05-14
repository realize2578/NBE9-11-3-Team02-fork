package com.back.together02be.users;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.global.util.JwtUtil;
import com.back.together02be.ranking.service.RankingSeasonService;
import com.back.together02be.users.dto.request.LoginReq;
import com.back.together02be.users.dto.request.SignupReq;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import com.back.together02be.users.service.UsersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

    @Mock UsersRepository usersRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RankingSeasonService rankingSeasonService;

    @InjectMocks
    UsersService usersService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(usersService, "jwtSecret", "test-secret-key-must-be-32-bytes!!");
        ReflectionTestUtils.setField(usersService, "accessExpireSeconds", 900L);
        ReflectionTestUtils.setField(usersService, "refreshExpireSeconds", 604800L);
    }

    @Test
    @DisplayName("정상 회원가입 — Users 저장, UserAccount(5000만원) 생성")
    void t1() {
        SignupReq req = new SignupReq("testuser", "password1!", "password1!", "닉네임");

        when(usersRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password1!")).thenReturn("encodedPassword");
        when(usersRepository.save(any(Users.class))).thenAnswer(inv -> inv.getArgument(0));

        usersService.signup(req);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getDeposit()).isEqualTo(50_000_000L);
        assertThat(captor.getValue().getTotalPurchase()).isEqualTo(0L);
    }

    @Test
    @DisplayName("중복 아이디 — IllegalArgumentException 발생")
    void t2() {
        SignupReq req = new SignupReq("testuser", "password1!", "password1!", "닉네임");

        when(usersRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(new Users("testuser", "encoded", "닉네임")));

        assertThatThrownBy(() -> usersService.signup(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용중인 아이디입니다.");

        verify(usersRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 불일치 — IllegalArgumentException 발생")
    void t3() {
        SignupReq req = new SignupReq("testuser", "password1!", "different!", "닉네임");

        when(usersRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersService.signup(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");

        verify(usersRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 로그인 — AccessToken, RefreshToken 반환 및 DB 저장")
    void t4() {
        LoginReq req = new LoginReq("testuser", "password1!");
        Users user = new Users("testuser", "encodedPassword", "닉네임");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(usersRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1!", "encodedPassword")).thenReturn(true);

        String[] tokens = usersService.login(req);
        Map<String, Object> payload = JwtUtil.payloadOrNull(tokens[0], "test-secret-key-must-be-32-bytes!!");

        assertThat(tokens).hasSize(2);
        assertThat(tokens[0]).isNotBlank();
        assertThat(tokens[1]).isNotBlank();
        assertThat(((Number) payload.get("id")).longValue()).isEqualTo(1L);
        assertThat(payload.get("username")).isEqualTo("testuser");
        assertThat(payload.get("nickname")).isEqualTo("닉네임");
        assertThat(user.getRefreshToken()).isEqualTo(tokens[1]);
        assertThat(user.getRefreshTokenExpiration()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("존재하지 않는 아이디 — IllegalArgumentException 발생")
    void t5() {
        LoginReq req = new LoginReq("nobody", "password1!");

        when(usersRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 오류 — IllegalArgumentException 발생")
    void t6() {
        LoginReq req = new LoginReq("testuser", "wrongpass!");
        Users user = new Users("testuser", "encodedPassword", "닉네임");

        when(usersRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass!", "encodedPassword")).thenReturn(false);

        assertThatThrownBy(() -> usersService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("정상 로그아웃 — RefreshToken 초기화")
    void t7() {
        Users user = new Users("testuser", "encoded", "닉네임");
        user.updateRefreshToken("valid-token", LocalDateTime.now().plusDays(7));

        when(usersRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(user));

        usersService.logout("valid-token");

        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshTokenExpiration()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 RefreshToken으로 로그아웃 — IllegalArgumentException 발생")
    void t8() {
        when(usersRepository.findByRefreshToken("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersService.logout("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 리프레시 토큰입니다.");
    }

    @Test
    @DisplayName("정상 토큰 재발급 — 새 AccessToken, RefreshToken 반환")
    void t9() {
        Users user = new Users("testuser", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.updateRefreshToken("old-token", LocalDateTime.now().plusDays(7));

        when(usersRepository.findByRefreshToken("old-token")).thenReturn(Optional.of(user));

        String[] tokens = usersService.reissueToken("old-token");
        Map<String, Object> payload = JwtUtil.payloadOrNull(tokens[0], "test-secret-key-must-be-32-bytes!!");

        assertThat(tokens).hasSize(2);
        assertThat(tokens[0]).isNotBlank();
        assertThat(tokens[1]).isNotBlank().isNotEqualTo("old-token");
        assertThat(((Number) payload.get("id")).longValue()).isEqualTo(1L);
        assertThat(payload.get("username")).isEqualTo("testuser");
        assertThat(payload.get("nickname")).isEqualTo("닉네임");
        assertThat(user.getRefreshToken()).isEqualTo(tokens[1]);
    }

    @Test
    @DisplayName("존재하지 않는 RefreshToken으로 재발급 — IllegalArgumentException 발생")
    void t10() {
        when(usersRepository.findByRefreshToken("ghost-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usersService.reissueToken("ghost-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 리프레시 토큰입니다.");
    }

    @Test
    @DisplayName("만료된 RefreshToken — IllegalArgumentException 발생")
    void t11() {
        Users user = new Users("testuser", "encoded", "닉네임");
        user.updateRefreshToken("expired-token", LocalDateTime.now().minusSeconds(1));

        when(usersRepository.findByRefreshToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usersService.reissueToken("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("리프레시 토큰이 만료되었습니다.");
    }
}
