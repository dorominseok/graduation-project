package com.fitness.backend.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰의 생성과 해시. API 명세서 2.3.
 *
 * <p>리프레시 토큰은 JWT가 아니라 <b>불투명 문자열</b>이다. 담을 정보가 없기
 * 때문이다 — 이 토큰이 하는 일은 "DB에 이 값이 살아 있는가"를 묻는 것뿐이고,
 * 그래야 즉시 폐기가 가능하다.
 */
@Component
public class OpaqueTokenFactory {

    /** 명세 2.3의 "랜덤 256bit". */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /**
     * 새 토큰 원문을 만든다. URL-safe Base64라 쿠키 값으로 그대로 쓸 수 있다.
     *
     * <p>{@code SecureRandom}을 쓴다. {@code Random}은 시드를 알면 다음 값을 예측할
     * 수 있어 인증 토큰에 쓸 수 없다.
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 저장·조회용 SHA-256 해시(64자 hex).
     *
     * <p>비밀번호와 달리 BCrypt 같은 느린 해시를 쓰지 않는다. 토큰은 사람이 고른
     * 값이 아니라 256비트 난수라 사전·무차별 대입이 성립하지 않고, 매 재발급마다
     * 해시를 계산해야 하므로 속도가 중요하다.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM 구현이 제공하도록 규정돼 있다.
            throw new IllegalStateException("SHA-256을 사용할 수 없다", e);
        }
    }
}
