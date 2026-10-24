package com.fitness.backend.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * V9 — 같은 날 기록을 하나로 합친다.
 *
 * <p>운영 DB에 이미 쌓인 기록이 있어서, 합치기 전후로 세트가 하나도 사라지지 않는지를 본다.
 * 따로 만든 스키마에 V8까지 올리고 옛 모양의 데이터를 넣은 뒤 V9를 적용한다.
 */
@SpringBootTest
class DailyRecordMigrationTest {

    private static final String SCHEMA = "v9_check";

    @Autowired DataSource dataSource;

    @AfterEach
    void dropSchema() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    @DisplayName("같은 날 기록을 합친다 — 세트는 그대로 옮겨지고 순번은 저장 시각 순, 메모는 잇고, 응원은 한 번만 남는다")
    void mergesSameDayRecords() throws SQLException {
        flyway("8").migrate();

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            // 풀이 돌려받을 때 되돌린다. SET search_path로 바꾸면 그 연결이 지운 스키마를 가리킨 채 다음 테스트로 간다
            c.setSchema(SCHEMA);
            st.execute("INSERT INTO users (email, password_hash, nickname) VALUES "
                    + "('me@example.com', 'x', '민석'), ('friend@example.com', 'x', '영희')");
            st.execute("INSERT INTO exercises (name_ko, body_part, measure_type, equipment) "
                    + "VALUES ('마이그레이션 벤치', 'CHEST', 'WEIGHT_REPS', 'BARBELL') ON CONFLICT (name_ko) DO NOTHING");
            long me = single(st, "SELECT id FROM users WHERE email = 'me@example.com'");
            long friend = single(st, "SELECT id FROM users WHERE email = 'friend@example.com'");
            long bench = single(st, "SELECT id FROM exercises WHERE name_ko = '마이그레이션 벤치'");

            // 10/10 아침(종료)과 저녁(진행 중) 두 기록, 10/9 하나
            long morning = insertSession(st, me, "2026-10-10", "DONE", "아침", 3000);
            long evening = insertSession(st, me, "2026-10-10", "DRAFT", "저녁", null);
            long other = insertSession(st, me, "2026-10-09", "DONE", null, 2400);
            insertSet(st, morning, bench, 1, "2026-10-10 07:00+09");
            insertSet(st, morning, bench, 2, "2026-10-10 07:05+09");
            insertSet(st, evening, bench, 1, "2026-10-10 19:00+09");
            insertSet(st, other, bench, 1, "2026-10-09 07:00+09");
            // 친구가 같은 날 두 기록에 모두 응원했다
            st.execute("INSERT INTO cheers (session_id, user_id) VALUES (" + morning + ", " + friend + "), ("
                    + evening + ", " + friend + ")");
        }

        flyway(null).migrate();

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            c.setSchema(SCHEMA);
            assertEquals(2, single(st, "SELECT count(*) FROM workout_sessions"));
            long day = single(st, "SELECT id FROM workout_sessions WHERE performed_on = '2026-10-10'");
            assertEquals(3, single(st, "SELECT count(*) FROM workout_sets WHERE session_id = " + day));
            assertEquals(List.of(1L, 2L, 3L), column(st,
                    "SELECT set_no FROM workout_sets WHERE session_id = " + day + " ORDER BY recorded_at"));
            assertEquals("아침\n저녁", text(st, "SELECT memo FROM workout_sessions WHERE id = " + day));
            assertEquals(1, single(st, "SELECT count(*) FROM cheers WHERE session_id = " + day));
            // 시간 보정값이 없던 날이라 보정값을 만들지 않는다 — 운동 시간은 세트로 다시 계산한다
            assertEquals(0, single(st, "SELECT count(*) FROM workout_sessions WHERE duration_override_sec IS NOT NULL"));
            // 다른 날은 그대로다
            assertEquals(1, single(st, "SELECT count(*) FROM workout_sets ws JOIN workout_sessions s "
                    + "ON s.id = ws.session_id WHERE s.performed_on = '2026-10-09'"));

            // 상태·시각 컬럼이 없어졌다
            assertFalse(hasColumn(st, "status"));
            assertFalse(hasColumn(st, "duration_sec"));
            // 하루에 하나
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO workout_sessions (user_id, performed_on) SELECT user_id, performed_on "
                            + "FROM workout_sessions WHERE id = " + day));
        }
    }

    @Test
    @DisplayName("합친 날에 시간 보정값이 있었으면 합친 기록들의 시간을 더한 값으로 남긴다")
    void keepsDurationOverrideWhenMerging() throws SQLException {
        flyway("8").migrate();

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            c.setSchema(SCHEMA);
            st.execute("INSERT INTO users (email, password_hash, nickname) VALUES ('me@example.com', 'x', '민석')");
            long me = single(st, "SELECT id FROM users");
            long first = insertSession(st, me, "2026-10-10", "DONE", null, 3000);
            st.execute("UPDATE workout_sessions SET duration_override_sec = 3600 WHERE id = " + first);
            insertSession(st, me, "2026-10-10", "DONE", null, 1200);
        }

        flyway(null).migrate();

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            c.setSchema(SCHEMA);
            // 사용자가 고친 3600 + 고치지 않은 기록의 산출값 1200
            assertEquals(4800, single(st, "SELECT duration_override_sec FROM workout_sessions"));
        }
    }

    // ── 도우미

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            // 옛 버전까지만 올릴 때는 반복 마이그레이션(종목 시드)을 돌리지 않는다. 시드는 늘 최신 스키마를
            // 전제한다 — V11의 starter_rank가 없는 V8 스키마에서는 실패한다. 이 테스트는 종목을 직접 넣는다
            config.target(target).repeatableSqlMigrationPrefix("NOT_REPEATABLE_");
        }
        return config.load();
    }

    private static long insertSession(Statement st, long userId, String date, String status, String memo,
                                      Integer durationSec) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "INSERT INTO workout_sessions (user_id, performed_on, status, source, memo, duration_sec) VALUES ("
                        + userId + ", '" + date + "', '" + status + "', 'LIVE', "
                        + (memo == null ? "NULL" : "'" + memo + "'") + ", "
                        + (durationSec == null ? "NULL" : durationSec) + ") RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void insertSet(Statement st, long sessionId, long exerciseId, int setNo, String recordedAt)
            throws SQLException {
        st.execute("INSERT INTO workout_sets (session_id, exercise_id, set_no, weight_kg, reps, recorded_at, "
                + "client_set_id) VALUES (" + sessionId + ", " + exerciseId + ", " + setNo + ", 60, 10, '"
                + recordedAt + "', gen_random_uuid())");
    }

    private static long single(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String text(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static List<Long> column(Statement st, String sql) throws SQLException {
        List<Long> values = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getLong(1));
            }
        }
        return values;
    }

    private static boolean hasColumn(Statement st, String column) throws SQLException {
        return single(st, "SELECT count(*) FROM information_schema.columns WHERE table_schema = '" + SCHEMA
                + "' AND table_name = 'workout_sessions' AND column_name = '" + column + "'") > 0;
    }
}
