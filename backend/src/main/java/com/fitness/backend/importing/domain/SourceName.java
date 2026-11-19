package com.fitness.backend.importing.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 원래 이름을 비교할 때 쓰는 열쇠. 띄어쓰기와 수식어 순서를 무시한다 —
 * {@code 숄더 프레스: 스탠딩, 원암, 덤벨}과 {@code 숄더프레스: 덤벨, 스탠딩, 원암}은 같은 이름이다.
 */
public final class SourceName {

    private SourceName() {
    }

    /** 띄어쓰기를 뺀 글 */
    public static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /** 원래 이름 전체({@code 이름: 수식어, 수식어})의 열쇠 */
    public static String key(String source) {
        String text = source == null ? "" : source.replace('：', ':');
        int colon = text.indexOf(':');
        if (colon < 0) {
            return compact(text);
        }
        String modifiers = Arrays.stream(text.substring(colon + 1).split(","))
                .map(SourceName::compact)
                .filter(word -> !word.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
        String name = compact(text.substring(0, colon));
        return modifiers.isEmpty() ? name : name + ":" + modifiers;
    }

    /** 원래 이름의 띄어쓰기 정리 — 앞뒤 공백을 떼고 여러 칸을 한 칸으로. 맞춘 이름 기억에 이 꼴로 저장한다 */
    public static String normalize(String source) {
        return source == null ? "" : source.replace(' ', ' ').strip().replaceAll("\\s+", " ");
    }
}
