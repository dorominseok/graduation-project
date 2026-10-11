package com.fitness.backend.group.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodeTest {

    @Test
    @DisplayName("6자리이고, 헷갈리는 글자(0·O, 1·I·L)가 나오지 않는다")
    void generatedCodeAvoidsConfusingCharacters() {
        for (int i = 0; i < 1000; i++) {
            String code = InviteCode.generate();
            assertEquals(InviteCode.LENGTH, code.length());
            assertTrue(code.chars().allMatch(c -> InviteCode.ALPHABET.indexOf(c) >= 0), code);
            assertTrue(code.chars().noneMatch(c -> "0O1IL".indexOf(c) >= 0), code);
        }
    }

    @Test
    @DisplayName("사람이 친 코드는 대문자로 모으고 공백·하이픈을 뺀다")
    void normalizesTypedCode() {
        assertEquals("K7P2QX", InviteCode.normalize(" k7p-2qx "));
        assertEquals("", InviteCode.normalize(null));
    }
}
