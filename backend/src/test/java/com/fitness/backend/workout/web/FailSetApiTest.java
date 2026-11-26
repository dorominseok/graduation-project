package com.fitness.backend.workout.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitness.backend.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패 세트(2026-11-28 사용자 요청, V21, 결정 기록 D-15). 목표 횟수(`targetReps`)를 두고 해낸 횟수는 `reps`.
 * 분석은 해낸 횟수의 본세트로 센다. 워밍업·시간 종목은 실패 세트가 될 수 없고, 목표는 해낸 횟수보다 커야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("unchecked")
class FailSetApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private String me;
    private long session;
    private long bench;
    private long plank;

    @BeforeEach
    void setUp() throws Exception {
        me = "Bearer " + authService.signUp("fail" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hunter2hunter2", "민석").tokens().accessToken();
        bench = idOf("덤벨 벤치프레스");
        plank = jdbc.queryForObject("select id from exercises where owner_id is null and measure_type = 'TIME' "
                + "order by id limit 1", Long.class);
        String body = mvc.perform(post("/api/v1/workout-sessions").header(HttpHeaders.AUTHORIZATION, me)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"performedOn\":\"" + LocalDate.now(clock).minusDays(1) + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        session = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    @DisplayName("실패 세트 저장 — 해낸 9회·목표 10회. 응답·하루 기록·직전 기록·종목 기록에 targetReps가 실린다, 메인 세트는 null")
    void saveAndRead() throws Exception {
        addSet(bench, "\"weightKg\":40,\"reps\":10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetReps").doesNotExist());
        addSet(bench, "\"weightKg\":40,\"reps\":9,\"targetReps\":10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.reps").value(9))
                .andExpect(jsonPath("$.targetReps").value(10))
                .andExpect(jsonPath("$.isWarmup").value(false));

        String day = body(get("/api/v1/workout-sessions/" + session));
        assertThat((List<Integer>) JsonPath.read(day, "$.exercises[0].sets[*].targetReps")).containsExactly(null, 10);

        String last = body(get("/api/v1/exercises/" + bench + "/last-performance"));
        assertThat((List<Integer>) JsonPath.read(last, "$.sets[*].targetReps")).containsExactly(null, 10);

        String history = body(get("/api/v1/exercises/" + bench + "/history"));
        assertThat((List<Integer>) JsonPath.read(history, "$..sets[*].targetReps")).containsExactly(null, 10);
    }

    @Test
    @DisplayName("저장할 수 없는 실패 세트 — 목표가 해낸 횟수 이하, 워밍업, 시간 종목이면 400")
    void invalidOnSave() throws Exception {
        addSet(bench, "\"weightKg\":40,\"reps\":10,\"targetReps\":10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEASURE_INPUT"))
                .andExpect(jsonPath("$.message").value("실패 세트는 해낸 횟수가 목표보다 적어야 합니다."));
        addSet(bench, "\"weightKg\":20,\"reps\":5,\"targetReps\":10,\"isWarmup\":true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("워밍업 세트는 실패 세트로 둘 수 없습니다."));
        addSet(plank, "\"durationSec\":40,\"targetReps\":10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("횟수로 재지 않는 종목은 실패 세트로 둘 수 없습니다."));
    }

    @Test
    @DisplayName("고치기 — 메인을 실패로, 해낸 횟수를 목표까지 올리면 400, targetReps 0이면 메인으로, 워밍업이 되면 목표는 저절로 지운다")
    void modify() throws Exception {
        long id = idOfSet(addSet(bench, "\"weightKg\":40,\"reps\":10").andExpect(status().isCreated()));

        patchSet(id, "{\"reps\":8,\"targetReps\":10}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reps").value(8))
                .andExpect(jsonPath("$.targetReps").value(10));
        patchSet(id, "{\"reps\":10}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("실패 세트는 해낸 횟수가 목표보다 적어야 합니다."));
        patchSet(id, "{\"targetReps\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reps").value(8))
                .andExpect(jsonPath("$.targetReps").doesNotExist());

        patchSet(id, "{\"targetReps\":12}").andExpect(status().isOk()).andExpect(jsonPath("$.targetReps").value(12));
        patchSet(id, "{\"isWarmup\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.isWarmup").value(true))
                .andExpect(jsonPath("$.targetReps").doesNotExist());
        patchSet(id, "{\"isWarmup\":true,\"targetReps\":12}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("워밍업 세트는 실패 세트로 둘 수 없습니다."));
    }

    @Test
    @DisplayName("분석 — 실패 세트는 해낸 횟수의 본세트로 센다(세트 수에 들어가고, 1RM은 해낸 횟수로)")
    void countedAsWorkingSet() throws Exception {
        addSet(bench, "\"weightKg\":40,\"reps\":10").andExpect(status().isCreated());
        addSet(bench, "\"weightKg\":40,\"reps\":6,\"targetReps\":10").andExpect(status().isCreated());
        addSet(bench, "\"weightKg\":20,\"reps\":12,\"isWarmup\":true").andExpect(status().isCreated());

        LocalDate day = LocalDate.now(clock).minusDays(1);
        String volume = body(get("/api/v1/analysis/muscle-volume").param("weeks", "1")
                .param("referenceDate", day.toString()));
        List<Number> chest = JsonPath.read(volume, "$.tiers[?(@.key == 'CHEST')].weeklySets");
        assertThat(chest.get(0).doubleValue()).isEqualTo(2.0);

        String intensity = body(get("/api/v1/stats/session-intensity/" + session));
        assertThat((List<Integer>) JsonPath.read(intensity, "$.exercises[0].sets[*].targetReps"))
                .containsExactly(null, 10, null);
    }

    // ── 도우미

    private ResultActions addSet(long exerciseId, String values) throws Exception {
        return mvc.perform(post("/api/v1/workout-sessions/" + session + "/sets").header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientSetId\":\"" + UUID.randomUUID() + "\",\"exerciseId\":" + exerciseId + "," + values + "}"));
    }

    private ResultActions patchSet(long setId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/workout-sessions/" + session + "/sets/" + setId)
                .header(HttpHeaders.AUTHORIZATION, me)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long idOfSet(ResultActions created) throws Exception {
        return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.id")).longValue();
    }

    private long idOf(String name) {
        return jdbc.queryForObject("select id from exercises where owner_id is null and name_ko = ?", Long.class, name);
    }

    private String body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, me)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
