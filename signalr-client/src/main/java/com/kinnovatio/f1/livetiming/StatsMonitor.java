package com.kinnovatio.f1.livetiming;

import com.google.auto.value.AutoValue;
import com.kinnovatio.signalr.messages.LiveTimingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@AutoValue
public abstract class StatsMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(StatsMonitor.class);
    private static final int DEFAULT_MAX_TIME_UNITS = 1800;  // 30 minutes + 30 hours
    private static final int DEFAULT_MESSAGE_QUEUE_SIZE = 10;

    private final Deque<LiveTimingMessage> messageQueue = new ArrayDeque<>(DEFAULT_MESSAGE_QUEUE_SIZE);

    private final AtomicInteger messageCounterPerSecond = new AtomicInteger(0);
    private final AtomicInteger messageCounterPerMinute = new AtomicInteger(0);
    private final Deque<RateTuple> messageCounterHistorySeconds = new ArrayDeque<>(DEFAULT_MAX_TIME_UNITS);
    private final Deque<RateTuple> messageCounterHistoryMinutes = new ArrayDeque<>(DEFAULT_MAX_TIME_UNITS);

    private ScheduledExecutorService executorService = null;

    private static StatsMonitor.Builder builder() {
        return new AutoValue_StatsMonitor.Builder()
                .setMaxTimeUnits(DEFAULT_MAX_TIME_UNITS)
                .setMessageQueueSize(DEFAULT_MESSAGE_QUEUE_SIZE);
    }

    public static StatsMonitor create() {
        return builder().build();
    }

    public abstract Builder toBuilder();

    public abstract int getMaxTimeUnits();
    public abstract int getMessageQueueSize();

    public StatsMonitor withMaxTimeUnits(int noUnits) {
        return toBuilder().setMaxTimeUnits(noUnits).build();
    }

    public StatsMonitor withMessageQueueSize(int size) {
        return toBuilder().setMessageQueueSize(size).build();
    }

    public StatsMonitor start() {
        if (executorService != null) {
            // the stats monitor is already running. Just return
            return this;
        }
        executorService = Executors.newSingleThreadScheduledExecutor();

        // Start a task to track the message rate per second
        executorService.scheduleAtFixedRate(() -> {
            // Get the current count and reset the counter
            int currentCountSeconds = messageCounterPerSecond.getAndSet(0);
            messageCounterHistorySeconds.add(new RateTuple(currentCountSeconds, Instant.now()));
            if (messageCounterHistorySeconds.size() > getMaxTimeUnits()) {
                messageCounterHistorySeconds.pollFirst();
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Start a task to track the message rate per minute
        executorService.scheduleAtFixedRate(() -> {
            // Get the current count and reset the counter
            int currentCountMinutes = messageCounterPerMinute.getAndSet(0);
            messageCounterHistoryMinutes.add(new RateTuple(currentCountMinutes, Instant.now()));
            if (messageCounterHistoryMinutes.size() > getMaxTimeUnits()) {
                messageCounterHistoryMinutes.pollFirst();
            }
        }, 1, 1, TimeUnit.MINUTES);

        return this;
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void addToMessageQueue(LiveTimingMessage message) {
        messageQueue.add(message);
        if (messageQueue.size() > getMessageQueueSize()) {
            messageQueue.pollFirst();
        }
    }

    public List<LiveTimingMessage> getMessagesFromQueue() {
        return List.copyOf(messageQueue);
    }

    public void incMessageCounter() {
        messageCounterPerSecond.incrementAndGet();
        messageCounterPerMinute.incrementAndGet();
    }

    public List<RateTuple> getMessageRatePerSecond() {
        return List.copyOf(messageCounterHistorySeconds);
    }

    public List<RateTuple> getMessageRatePerMinute() {
        return List.copyOf(messageCounterHistoryMinutes);
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setMaxTimeUnits(int value);
        abstract Builder setMessageQueueSize(int value);

        abstract StatsMonitor build();
    }
}
