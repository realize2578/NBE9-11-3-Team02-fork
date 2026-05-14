package com.back.together02be.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-32-bytes!!";
    private static final String OTHER_SECRET = "other-secret-key-must-be-32-bytes!";

    @Test
    @DisplayName("정상 access token - payload 읽기")
    void generateAccessToken_andReadPayload() {
        // given
        Map<String, Object> body = Map.of(
                "id", 1L,
                "username", "testuser",
                "nickname", "테스터"
        );

        // when
        String token = JwtUtil.generateAccessToken(SECRET, 3600L, body);
        Map payload = JwtUtil.payloadOrNull(token, SECRET);

        // then
        assertThat(token).isNotBlank();
        assertThat(payload).isNotNull();
        assertThat(((Number) payload.get("id")).longValue()).isEqualTo(1L);
        assertThat(payload.get("username")).isEqualTo("testuser");
        assertThat(payload.get("nickname")).isEqualTo("테스터");
    }

    @Test
    @DisplayName("정상 token 유효성 검증 - 성공")
    void validToken_returnsTrue() {
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of("id", 1L, "username", "testuser", "nickname", "테스터")
        );

        boolean result = JwtUtil.isValid(token, SECRET);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("다른 secret 검증 - 실패")
    void tokenWithDifferentSecret_returnsFalse() {
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of("id", 1L, "username", "testuser", "nickname", "테스터")
        );

        boolean result = JwtUtil.isValid(token, OTHER_SECRET);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("만료된 token 유효성 검증 - 실패")
    void expiredToken_returnsFalse() {
        String token = JwtUtil.generateAccessToken(
                SECRET,
                -1L,
                Map.of("id", 1L, "username", "testuser", "nickname", "테스터")
        );

        boolean result = JwtUtil.isValid(token, SECRET);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("잘못된 token payload - null")
    void malformedToken_payloadIsNull() {
        Map payload = JwtUtil.payloadOrNull("invalid.jwt.token", SECRET);

        assertThat(payload).isNull();
    }
}