package com.fitness.backend.auth.service;

import com.fitness.backend.auth.domain.RefreshToken;
import com.fitness.backend.auth.jwt.JwtProperties;
import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.auth.jwt.OpaqueTokenFactory;
import com.fitness.backend.auth.repository.RefreshTokenRepository;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.user.domain.User;
import com.fitness.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·로그인·재발급·로그아웃. API 명세서 4.1~4.4 / 2.3.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final OpaqueTokenFactory opaqueTokenFactory;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       OpaqueTokenFactory opaqueTokenFactory,
                       JwtProperties jwtProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.opaqueTokenFactory = opaqueTokenFactory;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** 회원가입. 가입 직후 로그인 상태가 되도록 토큰을 함께 발급한다(명세 4.1). */
    public AuthResult signUp(String email, String rawPassword, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = userRepository.save(
                User.signUp(email, passwordEncoder.encode(rawPassword), nickname));
        return new AuthResult(user, issueTokens(user));
    }

    /**
     * 로그인.
     *
     * <p>이메일이 없을 때와 비밀번호가 틀렸을 때 <b>같은 오류</b>를 준다(명세 4.2).
     * 구분해서 알려주면 "이 이메일이 가입돼 있는가"를 외부에서 확인할 수 있게 된다.
     */
    public AuthResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return new AuthResult(user, issueTokens(user));
    }

    /**
     * 액세스 토큰 재발급. <b>회전(rotation)</b>한다 — 쓴 토큰은 죽고 새 토큰이 나온다.
     *
     * <p>회전을 넣으면 "이미 죽은 토큰이 또 왔다"는 신호가 생긴다. 정상 클라이언트는
     * 새 토큰으로 갈아탔으므로 옛 토큰을 다시 보낼 이유가 없다. 그 신호는 <b>같은
     * 토큰을 가진 주체가 둘</b>이라는 뜻이고, 누가 도둑인지 알 수 없으므로 그 사용자의
     * 토큰을 전부 폐기해 비밀번호를 아는 쪽만 돌아오게 한다(명세 2.3).
     *
     * <p><b>유예 창</b>(LOG-16): 방금 폐기된 토큰이 다시 온 경우는 공격이 아니라
     * 동시 재발급으로 본다. 액세스 토큰이 만료되는 순간 여러 요청이 함께 401을 받아
     * 재발급이 두 번 발사될 수 있고, 이 앱은 운동 중 요청이 몰려 그 상황이 잦다.
     * 동시 요청은 밀리초 단위로 붙어 오지만 탈취는 그렇지 않으므로 시간으로 가른다.
     */
    public TokenPair refresh(String rawRefreshToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        RefreshToken token = refreshTokenRepository.findByTokenHash(opaqueTokenFactory.hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (token.isRevoked()) {
            if (token.getRevokedAt().isAfter(now.minus(jwtProperties.reuseGrace()))) {
                log.debug("유예 창 안의 폐기 토큰 재사용 — 동시 재발급으로 본다. userId={}", token.getUserId());
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            log.warn("리프레시 토큰 재사용 감지 — 사용자 토큰 전체 폐기. userId={}", token.getUserId());
            refreshTokenRepository.revokeAllByUserId(token.getUserId(), now);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        token.revoke(now);
        return issueTokens(user);
    }

    /**
     * 로그아웃. 리프레시 토큰을 폐기하고 쿠키를 만료시킨다(명세 4.4).
     *
     * <p>액세스 토큰은 남은 만료 시간(최대 30분) 동안 유효하다. 그 시간 창은 감수한다 —
     * 무상태를 유지하려고 JWT를 택한 것이므로 매 요청 폐기 목록을 조회하면 이유가 사라진다.
     */
    public void logout(Long userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(opaqueTokenFactory.hash(rawRefreshToken))
                // 남의 토큰을 넘겨 폐기시키지 못하게 소유자를 확인한다.
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(t -> t.revoke(OffsetDateTime.now(clock)));
    }

    private TokenPair issueTokens(User user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String raw = opaqueTokenFactory.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), opaqueTokenFactory.hash(raw), now.plus(jwtProperties.refreshTokenTtl())));
        // 재발급 때마다 만료가 다시 잡히므로, 계속 쓰는 사용자는 로그인이 끊기지 않는다.
        String access = jwtProvider.issueAccessToken(user.getId(), user.getEmail(), now.toInstant());
        return new TokenPair(access, jwtProvider.accessTokenTtlSeconds(), raw);
    }

    /** 가입·로그인 응답에 사용자 정보가 함께 필요하다(명세 4.1). */
    public record AuthResult(User user, TokenPair tokens) {
    }
}
