package com.fitness.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fitness.backend.auth.jwt.OpaqueTokenFactory;
import com.fitness.backend.common.error.ApiException;
import com.fitness.backend.common.error.ErrorCode;
import com.fitness.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 재사용 감지의 전체 폐기가 <b>DB에 남는지</b> 본다.
 *
 * <p>{@link RefreshRotationTest}는 테스트 전체가 한 트랜잭션이라, 폐기한 뒤 던진 예외가 그 폐기를 되돌려도
 * 알 수 없다. 여기서는 테스트에 트랜잭션을 걸지 않아 호출마다 실제로 커밋·롤백되고, 결과는 SQL로 직접 읽는다.
 * 롤백에 기대지 않으므로 만든 계정은 끝나고 지운다.
 */
@SpringBootTest
class RefreshReuseCommitTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired OpaqueTokenFactory opaqueTokenFactory;
    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("유예 창 밖의 재사용이 감지되면 전체 폐기가 커밋된다 — 도둑이 받아 간 토큰과 다른 기기 토큰으로 재발급할 수 없다")
    void revokeAllSurvivesTheRejection() {
        String email = "reuse" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        AuthService.AuthResult signedUp = authService.signUp(email, "hunter2hunter2", "민석");
        userId = signedUp.user().getId();
        String stolen = signedUp.tokens().refreshTokenRaw();
        String otherDevice = authService.login(email, "hunter2hunter2").tokens().refreshTokenRaw();

        // 도둑이 먼저 써서 새 토큰을 받아 간다
        String thief = authService.refresh(stolen).refreshTokenRaw();
        // 그 일이 유예 창(30초)보다 오래전이었던 것으로 만든다
        jdbc.update("update refresh_tokens set revoked_at = now() - interval '1 hour' where token_hash = ?",
                opaqueTokenFactory.hash(stolen));

        // 주인이 옛 토큰으로 재발급 → 재사용 감지
        assertInvalid(stolen);

        assertEquals(0, alive(), "전체 폐기가 DB에 남지 않았다");
        assertInvalid(thief);
        assertInvalid(otherDevice);
    }

    private void assertInvalid(String rawToken) {
        ApiException e = assertThrows(ApiException.class, () -> authService.refresh(rawToken));
        assertEquals(ErrorCode.TOKEN_INVALID, e.errorCode());
    }

    private int alive() {
        return jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, userId);
    }
}
