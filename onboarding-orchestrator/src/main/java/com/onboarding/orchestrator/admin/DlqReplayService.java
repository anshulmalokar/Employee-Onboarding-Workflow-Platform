package com.onboarding.orchestrator.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DlqReplayService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<Long, DlqRecord> records = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock replayLock = new ReentrantReadWriteLock();
    private final Lock readLock = replayLock.readLock();
    private final Lock writeLock = replayLock.writeLock();

    public DlqReplayService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public synchronized DlqRecord capture(String sourceTopic, String key, Object payload) {
        writeLock.lock();
        try {
            long id = sequence.getAndIncrement();
            DlqRecord record = new DlqRecord(
                id,
                sourceTopic,
                resolveTargetTopic(sourceTopic),
                key,
                payload,
                System.currentTimeMillis()
            );
            records.put(id, record);
            return record;
        } finally {
            writeLock.unlock();
        }
    }

    public List<DlqRecord> list() {
        readLock.lock();
        try {
            return records.values().stream()
                .sorted(Comparator.comparingLong(DlqRecord::capturedAtEpochMillis).reversed())
                .toList();
        } finally {
            readLock.unlock();
        }
    }

    public DlqReplayResponse replay(long recordId) {
        DlqRecord record;
        readLock.lock();
        try {
            record = records.get(recordId);
        } finally {
            readLock.unlock();
        }

        if (record == null) {
            return new DlqReplayResponse(recordId, false, "DLQ record not found");
        }

        kafkaTemplate.send(record.targetTopic(), record.key(), record.payload());
        return new DlqReplayResponse(recordId, true, "Replay submitted to " + record.targetTopic());
    }

    public List<DlqReplayResponse> replayAll(String sourceTopic) {
        List<DlqReplayResponse> responses = new ArrayList<>();

        for (DlqRecord record : list()) {
            if (sourceTopic != null && !sourceTopic.isBlank() && !sourceTopic.equals(record.sourceTopic())) {
                continue;
            }
            responses.add(replay(record.id()));
        }

        return responses;
    }

    private String resolveTargetTopic(String sourceTopic) {
        if (sourceTopic != null && sourceTopic.endsWith(".dlq")) {
            return sourceTopic.substring(0, sourceTopic.length() - 4);
        }
        return sourceTopic;
    }
}
