package com.fitness.backend.auth.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 구글 ID 토큰 검증 — 서명·발급자·받는 쪽·만료를 실제로 거절하는지.
 *
 * <p>구글의 비공개 키는 없으므로, 여기서 만든 키를 "구글의 키"로 쓴다. 공개 키를 어디서 받아 오는지만
 * 다르고 검증 규칙은 운영과 같은 것({@link GoogleTokenVerifier}가 붙이는 것)을 탄다.
 */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "our-app.apps.googleusercontent.com";

    private final KeyPair google = rsa();
    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier(new GoogleProperties(CLIENT_ID),
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) google.getPublic()).build());

    @Test
    @DisplayName("구글이 우리 앱에 발급한 토큰은 통과하고 회원번호·이메일·이름을 준다")
    void acceptsValidToken() {
        GoogleIdentity identity = verifier.verify(token(google, claims -> { }));

        assertEquals("1098765", identity.subject());
        assertEquals("user@gmail.com", identity.email());
        assertTrue(identity.emailVerified());
        assertEquals("이영희", identity.name());
    }

    @Test
    @DisplayName("발급자는 https가 붙지 않은 표기도 받는다 — 구글이 둘을 섞어 쓴다")
    void acceptsBothIssuerForms() {
        assertEquals("1098765", verifier.verify(token(google, c -> c.issuer("accounts.google.com"))).subject());
    }

    @Test
    @DisplayName("다른 앱에 발급된 토큰(aud가 다름)은 거절한다 — 남의 사이트가 받은 토큰으로 로그인할 수 없다")
    void rejectsOtherAudience() {
        assertRejected(token(google, c -> c.audience("another-app.apps.googleusercontent.com")));
    }

    @Test
    @DisplayName("aud가 없으면 거절한다")
    void rejectsMissingAudience() {
        assertRejected(token(google, c -> c.audience((String) null)));
    }

    @Test
    @DisplayName("발급자가 구글이 아니면 거절한다")
    void rejectsOtherIssuer() {
        assertRejected(token(google, c -> c.issuer("https://accounts.google.com.attacker.example")));
        assertRejected(token(google, c -> c.issuer(null)));
    }

    @Test
    @DisplayName("만료된 토큰은 거절한다")
    void rejectsExpired() {
        Instant past = Instant.now().minusSeconds(3600);
        assertRejected(token(google, c -> c.issueTime(Date.from(past.minusSeconds(3600))).expirationTime(Date.from(past))));
    }

    @Test
    @DisplayName("구글의 키로 서명하지 않은 토큰은 거절한다 — 다른 키, 그리고 공개 키를 비밀로 쓴 HS256")
    void rejectsForgedSignature() throws JOSEException {
        assertRejected(token(rsa(), claims -> { }));

        SignedJWT hs256 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), validClaims().build());
        hs256.sign(new MACSigner(google.getPublic().getEncoded()));
        assertRejected(hs256.serialize());
    }

    @Test
    @DisplayName("구글이 확인하지 않은 이메일은 확인 안 됨으로 넘긴다 — 묶을지는 AuthService가 정한다")
    void passesUnverifiedEmailFlag() {
        assertFalse(verifier.verify(token(google, c -> c.claim("email_verified", false))).emailVerified());
        assertFalse(verifier.verify(token(google, c -> c.claim("email_verified", null))).emailVerified());
    }

    @Test
    @DisplayName("클라이언트 ID가 설정되지 않았으면 무엇이 와도 거절한다")
    void rejectsWhenDisabled() {
        GoogleTokenVerifier disabled = new GoogleTokenVerifier(new GoogleProperties(""),
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) google.getPublic()).build());

        ApiException e = assertThrows(ApiException.class, () -> disabled.verify(token(google, c -> c.audience(""))));
        assertEquals(ErrorCode.GOOGLE_LOGIN_FAILED, e.errorCode());
    }

    private void assertRejected(String credential) {
        ApiException e = assertThrows(ApiException.class, () -> verifier.verify(credential));
        assertEquals(ErrorCode.GOOGLE_LOGIN_FAILED, e.errorCode());
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("1098765")
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .claim("name", "이영희")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
    }

    private static String token(KeyPair signer, Consumer<JWTClaimsSet.Builder> change) {
        JWTClaimsSet.Builder claims = validClaims();
        change.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        try {
            jwt.sign(new RSASSASigner(signer.getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
