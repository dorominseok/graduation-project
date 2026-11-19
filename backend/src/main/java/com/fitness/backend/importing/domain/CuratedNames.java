package com.fitness.backend.importing.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 미리 만든 짝 표 — 바디캘린더 이름 → 우리 기본 종목 이름(「기록가져오기_설계서」 4장 3번).
 *
 * <p>{@code data/import_bodycalendar.csv}를 시작할 때 한 번 읽는다. 원래 이름에 쉼표가 들어가므로
 * ({@code 숄더 프레스: 스탠딩, 원암, 덤벨}) 그 칸은 큰따옴표로 감싼다. 대상 이름에는 쉼표가 없다.
 */
@Component
public class CuratedNames {

    static final String RESOURCE = "data/import_bodycalendar.csv";

    /** 원래 이름 그대로 → 대상 이름. 표의 순서를 지킨다 */
    private final Map<String, String> rows;
    /** {@link SourceName#key} → 대상 이름 */
    private final Map<String, String> byKey;

    public CuratedNames() {
        this.rows = read();
        Map<String, String> keyed = new LinkedHashMap<>();
        rows.forEach((source, target) -> keyed.put(SourceName.key(source), target));
        this.byKey = Map.copyOf(keyed);
    }

    public Map<String, String> rows() {
        return rows;
    }

    public Map<String, String> byKey() {
        return byKey;
    }

    private static Map<String, String> read() {
        Map<String, String> rows = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // 머리줄
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int comma = line.lastIndexOf(',');
                if (comma <= 0) {
                    throw new IllegalStateException(RESOURCE + "의 줄을 읽을 수 없다: " + line);
                }
                String source = line.substring(0, comma).strip();
                if (source.length() >= 2 && source.startsWith("\"") && source.endsWith("\"")) {
                    source = source.substring(1, source.length() - 1);
                }
                rows.put(SourceName.normalize(source), line.substring(comma + 1).strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(RESOURCE + "를 읽지 못했다", e);
        }
        return Collections.unmodifiableMap(rows);
    }
}
