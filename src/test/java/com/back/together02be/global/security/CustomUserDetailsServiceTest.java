package com.back.together02be.global.security;

import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    UsersRepository usersRepository;

    @Test
    @DisplayName("username으로 사용자 찾기 - SecurityUser 반환")
    void loadUserByUsername_success() {
        // given
        Users user = new Users("testuser", "encodedPassword", "테스터");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(usersRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        CustomUserDetailsService service = new CustomUserDetailsService(usersRepository);

        // when
        UserDetails result = service.loadUserByUsername("testuser");

        // then
        assertThat(result).isInstanceOf(SecurityUser.class);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("encodedPassword");

        SecurityUser securityUser = (SecurityUser) result;

        assertThat(securityUser.getId()).isEqualTo(1L);
        assertThat(securityUser.getNickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("존재하지 않는 username - UsernameNotFoundException")
    void loadUserByUsername_notFound() {
        // given
        when(usersRepository.findByUsername("missing"))
                .thenReturn(Optional.empty());

        CustomUserDetailsService service = new CustomUserDetailsService(usersRepository);

        // when & then
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("존재하지 않는 아이디입니다: missing");
    }
}