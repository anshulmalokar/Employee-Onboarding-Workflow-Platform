package com.onboarding.contracts.idempotency;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyGuard {

    private final int maxEntries;
    private final ConcurrentHashMap<String, Long> processedKeys = new ConcurrentHashMap<>();

    public InMemoryIdempotencyGuard(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public boolean shouldProcess(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }

        Long existing = processedKeys.putIfAbsent(key, System.currentTimeMillis());
        if (existing != null) {
            return false;
        }

        if (processedKeys.size() > maxEntries) {
            trimOldestEntries();
        }

        return true;
    }

    private void trimOldestEntries() {
        int removeCount = Math.max(1, maxEntries / 10);
        processedKeys.entrySet().stream()
            .sorted(Comparator.comparingLong(Map.Entry::getValue))
            .limit(removeCount)
            .map(Map.Entry::getKey)
            .toList()
            .forEach(processedKeys::remove);
    }
}
