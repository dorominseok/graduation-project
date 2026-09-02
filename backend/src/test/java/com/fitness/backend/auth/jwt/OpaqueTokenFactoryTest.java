package com.fitness.backend.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 리프레시 토큰 생성·해시. 명세 2.3. */
class OpaqueTokenFactoryTest {

    private final OpaqueTokenFactory factory = new OpaqueTokenFactory();

    @Test
    @DisplayName("매번 다른 값이 나온다")
    void generatesDistinctTokens() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(factory.generate()), "중복 생성됨");
        }
    }

    @Test
    @DisplayName("쿠키 값으로 바로 쓸 수 있는 문자만 나온다")
    void urlSafeCharactersOnly() {
        for (int i = 0; i < 100; i++) {
            assertTrue(factory.generate().matches("[A-Za-z0-9_-]+"));
        }
    }

    @Test
    @DisplayName("해시는 64자 hex이고 같은 입력에 같은 값을 준다 — 조회 키로 쓸 수 있어야 한다")
    void hashIsStableHex() {
        String raw = factory.generate();
        String hash = factory.hash(raw);
        assertTrue(hash.matches("[0-9a-f]{64}"), "SHA-256 hex가 아님: " + hash);
        assertEquals(hash, factory.hash(raw));
    }

    @Test
    @DisplayName("다른 토큰은 다른 해시를 갖는다")
    void differentTokensDifferentHashes() {
        assertNotEquals(factory.hash(factory.generate()), factory.hash(factory.generate()));
    }

    @Test
    @DisplayName("저장하는 값에서 원문을 알 수 없다 — DB가 유출돼도 토큰을 재구성할 수 없다")
    void hashDoesNotContainRaw() {
        String raw = factory.generate();
        assertNotEquals(raw, factory.hash(raw));
        assertTrue(!factory.hash(raw).contains(raw));
    }
}
