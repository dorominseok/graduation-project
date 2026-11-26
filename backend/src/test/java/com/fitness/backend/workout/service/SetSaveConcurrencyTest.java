package com.fitness.backend.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitness.backend.auth.service.AuthService;
import com.fitness.backend.exercise.repository.ExerciseRepository;
import com.fitness.backend.user.repository.UserRepository;
import com.fitness.backend.workout.repository.WorkoutSetRepository;
import com.fitness.backend.workout.web.WorkoutDtos.CreateSetRequest;
import com.fitness.backend.workout.web.WorkoutDtos.OpenDayRequest;
import com.fitness.backend.workout.web.WorkoutDtos.SetSaveResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 같은 세트가 동시에 들어올 때. 요청마다 따로 커밋돼야 경합이 생기므로 {@code @Transactional}을 걸지
 * 않고, 만든 사용자를 끝나고 지운다(기록·세트는 따라 지워진다).
 */
@SpringBootTest
class SetSaveConcurrencyTest {

    private static final int REQUESTS = 12;

    @Autowired WorkoutService workoutService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired ExerciseRepository exerciseRepository;
    @Autowired WorkoutSetRepository setRepository;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("같은 clientSetId가 동시에 와도 한 번만 저장되고, 나머지는 오류 없이 그 세트를 받는다")
    void sameKeyAtOnceSavesOnce() throws Exception {
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        userId = authService.signUp(email, "hunter2hunter2", "경합").user().getId();
        Long sessionId = workoutService.openDay(userId, new OpenDayRequest(null)).session().id();
        Long exerciseId = exerciseRepository.findAll().stream()
                .filter(e -> e.getMeasureType().supportsOneRm())
                .findFirst().orElseThrow().getId();
        CreateSetRequest request = new CreateSetRequest(
                UUID.randomUUID(), exerciseId, new BigDecimal("60.0"), 10, null, false, null);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        List<Object> results = new ArrayList<>();
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return workoutService.addSet(userId, sessionId, request);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
        } finally {
            pool.shutdown();
        }

        assertTrue(results.stream().noneMatch(Throwable.class::isInstance), results.toString());
        List<SetSaveResult> saved = results.stream().map(SetSaveResult.class::cast).toList();
        assertEquals(1, saved.stream().filter(SetSaveResult::created).count());
        assertEquals(1, saved.stream().map(r -> r.set().id()).distinct().count());
        assertEquals(1, setRepository.countBySessionId(sessionId));
    }
}
