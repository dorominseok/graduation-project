package com.fitness.backend.auth.google;

import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 화면이 구글에서 받아 온 ID 토큰을 검증한다.
 *
 * <p>ID 토큰은 구글이 비공개 키로 서명한 JWT다. 구글의 공개 키(JWKS)로 서명을 확인하고,
 * 발급자가 구글인지, <b>우리 클라이언트 ID로 발급됐는지</b>, 만료되지 않았는지를 본다.
 * 받는 쪽(aud)을 확인하지 않으면 다른 사이트가 받은 토큰을 가져와 우리 앱에 로그인할 수 있다.
 *
 * <p>공개 키는 처음 검증할 때 받아 와 캐시한다. 구글 토큰은 확인만 하고 저장하지 않는다 —
 * 로그인 뒤에는 우리 토큰(액세스·리프레시)을 쓴다.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    /** 구글은 두 표기를 섞어 쓴다. */
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleProperties properties;
    private final JwtDecoder decoder;

    public GoogleTokenVerifier(GoogleProperties properties) {
        this.properties = properties;
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                claim("iss", jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))),
                claim("aud", jwt -> jwt.getAudience() != null && jwt.getAudience().contains(properties.clientId())))));
        this.decoder = nimbus;
    }

    public GoogleIdentity verify(String credential) {
        if (!properties.enabled()) {
            throw new ApiException(ErrorCode.GOOGLE_LOGIN_FAILED);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(credential);
        } catch (JwtException e) {
            log.info("구글 ID 토큰 검증 실패: {}", e.getMessage());
            throw new ApiException(ErrorCode.GOOGLE_LOGIN_FAILED);
        }
        return new GoogleIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    private static OAuth2TokenValidator<Jwt> claim(String name, java.util.function.Predicate<Jwt> test) {
        OAuth2Error error = new OAuth2Error("invalid_token", name + " 값이 맞지 않는다", null);
        return jwt -> test.test(jwt) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(error);
    }
}
