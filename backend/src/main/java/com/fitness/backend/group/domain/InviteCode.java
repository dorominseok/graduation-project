package com.fitness.backend.group.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 그룹 초대코드.
 *
 * <p>대문자·숫자 6자리이고, 손으로 옮겨 적을 때 헷갈리는 글자(0·O, 1·I·L)를 뺐다.
 * 31자 6자리면 약 8.9억 가지라 지인 그룹 규모에서 맞혀 들어오기는 어렵다.
 * 대부분은 초대 링크로 들어오고, 코드 입력은 링크를 못 쓸 때의 대안이다.
 */
public final class InviteCode {

    public static final int LENGTH = 6;
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCode() {
    }

    /** 순번처럼 이어지는 값이면 남의 그룹 코드를 짐작할 수 있다. 무작위로 만든다 */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** 사용자가 친 코드를 저장된 형태로. 소문자·공백·하이픈은 받아 준다 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
