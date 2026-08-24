package org.havenapp.main.service;

import org.havenapp.main.model.EventTrigger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

final class SensorFusion {
    private static final long WINDOW_MS = 45_000L;
    private static final int FUSION_THRESHOLD = 55;

    private static final class Observation {
        final int type;
        final long timestamp;

        Observation(int type, long timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    private final Deque<Observation> observations = new ArrayDeque<>();

    synchronized Result observe(int alertType) {
        long now = System.currentTimeMillis();
        observations.addLast(new Observation(alertType, now));
        while (!observations.isEmpty() && now - observations.peekFirst().timestamp > WINDOW_MS) {
            observations.removeFirst();
        }

        Map<Integer, Integer> counts = new HashMap<>();
        for (Observation observation : observations) {
            counts.merge(observation.type, 1, Integer::sum);
        }

        boolean motion = hasSignal(counts, EventTrigger.ACCELEROMETER, EventTrigger.BUMP);
        boolean audio = hasSignal(counts, EventTrigger.MICROPHONE);
        boolean emf = hasSignal(counts, EventTrigger.EMF);
        boolean camera = hasSignal(counts, EventTrigger.CAMERA);
        boolean environment = hasSignal(counts,
                EventTrigger.LIGHT, EventTrigger.PRESSURE, EventTrigger.POWER);

        int score = 0;
        score += motion ? 30 : 0;
        score += audio ? 28 : 0;
        score += emf ? 25 : 0;
        score += camera ? 22 : 0;
        score += environment ? 8 : 0;
        score += Math.min(12, observations.size() * 2);
        if (score >= FUSION_THRESHOLD) score += 15;

        return new Result(Math.min(100, score), score >= FUSION_THRESHOLD,
                motion && audio && emf);
    }

    private static boolean hasSignal(Map<Integer, Integer> counts, int... types) {
        for (int type : types) {
            if (counts.getOrDefault(type, 0) > 0) return true;
        }
        return false;
    }

    static class Result {
        final int score;
        final boolean highPriority;
        final boolean tripleCorrelation;

        Result(int score, boolean highPriority, boolean tripleCorrelation) {
            this.score = score;
            this.highPriority = highPriority;
            this.tripleCorrelation = tripleCorrelation;
        }
    }
}
