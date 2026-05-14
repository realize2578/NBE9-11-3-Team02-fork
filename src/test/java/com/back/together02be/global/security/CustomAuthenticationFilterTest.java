package com.back.together02be.global.security;

import com.back.together02be.global.util.JwtUtil;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-must-be-32-bytes!!";

    private CustomAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CustomAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상 Bearer token이면 SecurityContext에 인증 정보가 저장된다")
    void validBearerToken_setsAuthentication() throws ServletException, IOException {
        // given
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of(
                        "id", 1L,
                        "username", "testuser",
                        "nickname", "테스터"
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(SecurityUser.class);

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();

        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo("testuser");
        assertThat(principal.getNickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("token없으면 인증 정보 저장 안하고 다음 필터로 패스")
    void noToken_doesNotSetAuthentication() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNull();
    }

    @Test
    @DisplayName("잘못된 Bearer token이면 인증 정보를 저장 안함")
    void invalidBearerToken_doesNotSetAuthentication() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNull();
    }

    @Test
    @DisplayName("Bearer prefix가 아니면 Authorization 헤더를 token으로 사용 안함")
    void nonBearerHeader_doesNotSetAuthentication() throws ServletException, IOException {
        // given
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of("id", 1L, "username", "testuser", "nickname", "테스터")
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNull();
    }

    @Test
    @DisplayName("URL parameter token으로도 인증 정보가 저장")
    void queryParameterToken_setsAuthentication() throws ServletException, IOException {
        // given
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of(
                        "id", 1L,
                        "username", "testuser",
                        "nickname", "테스터"
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(SecurityUser.class);
    }

    @Test
    @DisplayName("payload에 필수 claim 없으면 인증 정보 저장암함")
    void missingRequiredClaims_doesNotSetAuthentication() throws ServletException, IOException {
        // given
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of(
                        "id", 1L,
                        "username", "testuser"
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNull();
    }
}