package com.onboarding.onboarding.repository;

import com.onboarding.onboarding.domain.OnboardingRequest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingRepository {

    private final ConcurrentHashMap<String, OnboardingRequest> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyKeyIndex = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock repositoryLock = new ReentrantReadWriteLock();
    private final Lock readLock = repositoryLock.readLock();
    private final Lock writeLock = repositoryLock.writeLock();

    public OnboardingRequest save(OnboardingRequest request) {
        writeLock.lock();
        try {
            store.put(request.getSagaId(), request);
            return request;
        } finally {
            writeLock.unlock();
        }
    }

    public Optional<OnboardingRequest> findBySagaId(String sagaId) {
        readLock.lock();
        try {
            return Optional.ofNullable(store.get(sagaId));
        } finally {
            readLock.unlock();
        }
    }

    public Optional<OnboardingRequest> findByIdempotencyKey(String idempotencyKey) {
        readLock.lock();
        try {
            String sagaId = idempotencyKeyIndex.get(idempotencyKey);
            if (sagaId == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(sagaId));
        } finally {
            readLock.unlock();
        }
    }

    public void mapIdempotencyKey(String idempotencyKey, String sagaId) {
        writeLock.lock();
        try {
            idempotencyKeyIndex.putIfAbsent(idempotencyKey, sagaId);
        } finally {
            writeLock.unlock();
        }
    }
}
