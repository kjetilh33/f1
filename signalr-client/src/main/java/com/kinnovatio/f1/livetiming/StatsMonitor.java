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

/**
 * Monitors statistics for F1 live timing data stream.
 * <p>
 * This class tracks the rate of incoming messages per second and per minute,
 * and maintains a queue of the most recent messages.
 * It is designed to be thread-safe.
 * <p>
 * Use the {@link #create()} method for a default instance or the builder
 * for a customized one. The monitor must be started with {@link #start()}
 * to begin collecting statistics.
 */
@AutoValue
public abstract class StatsMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(StatsMonitor.class);
    /**
     * The default maximum number of time units (seconds or minutes) to store in the history.
     * Set to 120, meaning it will store 120 seconds (2 minutes) of per-second rates and 120 minutes (2 hours) of per-minute rates.
     */
    private static final int DEFAULT_MAX_TIME_UNITS = 120;
    /** The default size of the message queue which stores the most recent messages. */
    private static final int DEFAULT_MESSAGE_QUEUE_SIZE = 20;

    private final Deque<LiveTimingMessage> messageQueue = new ArrayDeque<>(DEFAULT_MESSAGE_QUEUE_SIZE);

    private final AtomicInteger messageCounterPerSecond = new AtomicInteger(0);
    private final AtomicInteger messageCounterPerMinute = new AtomicInteger(0);
    private final Deque<RateTuple> messageCounterHistorySeconds = new ArrayDeque<>(DEFAULT_MAX_TIME_UNITS);
    private final Deque<RateTuple> messageCounterHistoryMinutes = new ArrayDeque<>(DEFAULT_MAX_TIME_UNITS);

    private ScheduledExecutorService executorService = null;

    /**
     * Returns a builder for {@link StatsMonitor} initialized with default values.
     *
     * @return a new {@link Builder} instance.
     */
    private static StatsMonitor.Builder builder() {
        return new AutoValue_StatsMonitor.Builder()
                .setMaxTimeUnits(DEFAULT_MAX_TIME_UNITS)
                .setMessageQueueSize(DEFAULT_MESSAGE_QUEUE_SIZE);
    }

    /**
     * Creates a new {@link StatsMonitor} with default settings.
     *
     * @return a new {@link StatsMonitor} instance.
     */
    public static StatsMonitor create() {
        return builder().build();
    }

    /**
     * Creates a builder for a {@link StatsMonitor} instance, initialized with the values of this instance.
     * @return a new {@link Builder} instance.
     */
    public abstract Builder toBuilder();

    /**
     * Gets the maximum number of time units (seconds or minutes) to store in the rate history.
     * @return the maximum number of time units.
     */
    public abstract int getMaxTimeUnits();
    /**
     * Gets the maximum size of the recent messages queue.
     * @return the message queue size.
     */
    public abstract int getMessageQueueSize();

    /**
     * Creates a new {@link StatsMonitor} instance with the specified maximum number of time units for history.
     *
     * @param noUnits the maximum number of time units.
     * @return a new {@link StatsMonitor} instance.
     */
    public StatsMonitor withMaxTimeUnits(int noUnits) {
        return toBuilder().setMaxTimeUnits(noUnits).build();
    }

    /**
     * Creates a new {@link StatsMonitor} instance with the specified message queue size.
     *
     * @param size the size of the message queue.
     * @return a new {@link StatsMonitor} instance.
     */
    public StatsMonitor withMessageQueueSize(int size) {
        return toBuilder().setMessageQueueSize(size).build();
    }

    /**
     * Starts the statistics monitoring.
     * <p>
     * This method initializes and starts scheduled tasks to track message rates per second and per minute.
     * If the monitor is already running, this method does nothing.
     *
     * @return this {@link StatsMonitor} instance.
     */
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

    /**
     * Stops the statistics monitoring.
     * <p>
     * This method shuts down the scheduled tasks. It should be called to clean up resources
     * when the monitor is no longer needed.
     */
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Adds a {@link LiveTimingMessage} to the recent messages queue.
     * <p>
     * If the queue is full, the oldest message is removed.
     *
     * @param message the message to add.
     */
    public void addToMessageQueue(LiveTimingMessage message) {
        messageQueue.add(message);
        if (messageQueue.size() > getMessageQueueSize()) {
            messageQueue.pollFirst();
        }
    }

    /**
     * Returns an immutable list of the most recent messages from the queue.
     *
     * @return a {@link List} of {@link LiveTimingMessage}.
     */
    public List<LiveTimingMessage> getMessagesFromQueue() {
        return List.copyOf(messageQueue);
    }

    /**
     * Increments the message counters for both per-second and per-minute rates.
     * This should be called for each message received.
     */
    public void incMessageCounter() {
        messageCounterPerSecond.incrementAndGet();
        messageCounterPerMinute.incrementAndGet();
    }

    /**
     * Returns an immutable list of the message rate per second over time.
     * Each entry in the list is a {@link RateTuple} containing the count of messages
     * for a specific second and the timestamp when it was recorded.
     *
     * @return a {@link List} of {@link RateTuple}.
     */
    public List<RateTuple> getMessageRatePerSecond() {
        return List.copyOf(messageCounterHistorySeconds);
    }

    /**
     * Returns an immutable list of the message rate per minute over time.
     * Each entry in the list is a {@link RateTuple} containing the count of messages
     * for a specific minute and the timestamp when it was recorded.
     *
     * @return a {@link List} of {@link RateTuple}.
     */
    public List<RateTuple> getMessageRatePerMinute() {
        return List.copyOf(messageCounterHistoryMinutes);
    }

    /**
     * A builder for creating {@link StatsMonitor} instances.
     */
    @AutoValue.Builder
    abstract static class Builder {
        /**
         * Sets the maximum number of time units (seconds or minutes) to store in the rate history.
         * @param value the maximum number of time units.
         * @return this builder.
         */
        abstract Builder setMaxTimeUnits(int value);
        /**
         * Sets the maximum size of the recent messages queue.
         * @param value the message queue size.
         * @return this builder.
         */
        abstract Builder setMessageQueueSize(int value);

        /**
         * Builds and returns a {@link StatsMonitor} with the configured properties.
         * @return a new {@link StatsMonitor} instance.
         */
        abstract StatsMonitor build();
    }
}
