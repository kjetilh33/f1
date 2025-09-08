package com.kinnovatio.f1.livetiming;

import com.google.auto.value.AutoValue;
import com.kinnovatio.signalr.messages.LiveTimingMessage;

import java.time.Duration;
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
    private static Duration DEFAULT_MAX_TIME_WINDOW = Duration.ofMinutes(30);
    private static final int DEFAULT_MESSAGE_QUEUE_SIZE = 10;

    private final Deque<LiveTimingMessage> messageQueue = new ArrayDeque<>(DEFAULT_MESSAGE_QUEUE_SIZE);

    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final Deque<RateTuple> messageCounterHistory = new ArrayDeque<>((int) DEFAULT_MAX_TIME_WINDOW.toSeconds());

    private ScheduledExecutorService executorService = null;

    private static StatsMonitor.Builder builder() {
        return new AutoValue_StatsMonitor.Builder()
                .setMaxTimeWindow(DEFAULT_MAX_TIME_WINDOW)
                .setMessageQueueSize(DEFAULT_MESSAGE_QUEUE_SIZE);
    }

    public static StatsMonitor create() {
        return builder().build();
    }

    public abstract Builder toBuilder();

    public abstract Duration getMaxTimeWindow();
    public abstract int getMessageQueueSize();

    public StatsMonitor withMaxTimeWindow(Duration duration) {
        return toBuilder().setMaxTimeWindow(duration).build();
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
        executorService.scheduleAtFixedRate(() -> {
            // Get the current count and reset the counter
            int currentCount = messageCounter.getAndSet(0);
            messageCounterHistory.add(new RateTuple(currentCount, Instant.now()) );
        }, 1, 1, TimeUnit.SECONDS);

        return this;
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
        messageCounter.incrementAndGet();
    }

    public List<RateTuple> getMessageRate() {
        return List.copyOf(messageCounterHistory);
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setMaxTimeWindow(Duration value);
        abstract Builder setMessageQueueSize(int value);

        abstract StatsMonitor build();
    }
}
