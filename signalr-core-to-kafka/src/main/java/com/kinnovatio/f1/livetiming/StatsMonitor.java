package com.kinnovatio.f1.livetiming;

import com.kinnovatio.signalr.messages.LiveTimingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// Monitors statistics for F1 live timing data stream.
///
/// This class tracks the rate of incoming messages per second and per minute,
/// and maintains a queue of the most recent messages.
/// It is designed to be thread-safe.
public final class StatsMonitor {
    private static final int DEFAULT_MAX_TIME_UNITS = 120;
    private static final int DEFAULT_MESSAGE_QUEUE_SIZE = 20;

    private final int maxTimeUnits;
    private final int messageQueueSize;

    private final Deque<LiveTimingMessage> messageQueue;
    private final Deque<RateTuple> messageCounterHistorySeconds;
    private final Deque<RateTuple> messageCounterHistoryMinutes;

    private final AtomicInteger messageCounterPerSecond = new AtomicInteger(0);
    private final AtomicInteger messageCounterPerMinute = new AtomicInteger(0);

    private ScheduledExecutorService executorService = null;

    private StatsMonitor(int maxTimeUnits, int messageQueueSize) {
        this.maxTimeUnits = maxTimeUnits;
        this.messageQueueSize = messageQueueSize;
        this.messageQueue = new ArrayDeque<>(messageQueueSize);
        this.messageCounterHistorySeconds = new ConcurrentLinkedDeque<>();
        this.messageCounterHistoryMinutes = new ConcurrentLinkedDeque<>();
    }

    /// Creates a new [StatsMonitor] with default settings.
    ///
    /// @return a new [StatsMonitor] instance.
    public static StatsMonitor create() {
        return new StatsMonitor(DEFAULT_MAX_TIME_UNITS, DEFAULT_MESSAGE_QUEUE_SIZE);
    }

    public int getMaxTimeUnits() {
        return maxTimeUnits;
    }

    public int getMessageQueueSize() {
        return messageQueueSize;
    }

    /// Creates a new [StatsMonitor] instance with the specified maximum number of time units for history.
    ///
    /// @param noUnits the maximum number of time units.
    /// @return a new [StatsMonitor] instance.
    public StatsMonitor withMaxTimeUnits(int noUnits) {
        return new StatsMonitor(noUnits, this.messageQueueSize);
    }

    /// Creates a new [StatsMonitor] instance with the specified message queue size.
    ///
    /// @param size the size of the message queue.
    /// @return a new [StatsMonitor] instance.
    public StatsMonitor withMessageQueueSize(int size) {
        return new StatsMonitor(this.maxTimeUnits, size);
    }

    /// Starts the statistics monitoring.
    ///
    /// This method initializes and starts scheduled tasks to track message rates per second and per minute.
    /// If the monitor is already running, this method does nothing.
    ///
    /// @return this [StatsMonitor] instance.
    public synchronized StatsMonitor start() {
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

    /// Stops the statistics monitoring.
    ///
    /// This method shuts down the scheduled tasks. It should be called to clean up resources
    /// when the monitor is no longer needed.
    public synchronized void stop() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    /// Adds a [LiveTimingMessage] to the recent messages queue.
    ///
    /// If the queue is full, the oldest message is removed.
    ///
    /// @param message the message to add.
    public void addToMessageQueue(LiveTimingMessage message) {
        messageQueue.add(message);
        if (messageQueue.size() > getMessageQueueSize()) {
            messageQueue.pollFirst();
        }
    }

    /// Returns an immutable list of the most recent messages from the queue.
    ///
    /// @return a [List] of [LiveTimingMessage].
    public List<LiveTimingMessage> getMessagesFromQueue() {
        return List.copyOf(messageQueue);
    }

    /// Increments the message counters for both per-second and per-minute rates.
    /// This should be called for each message received.
    public void incMessageCounter() {
        messageCounterPerSecond.incrementAndGet();
        messageCounterPerMinute.incrementAndGet();
    }

    /// Returns an immutable list of the message rate per second over time.
    /// Each entry in the list is a [RateTuple] containing the count of messages
    /// for a specific second and the timestamp when it was recorded.
    ///
    /// @return a [List] of [RateTuple].
    public List<RateTuple> getMessageRatePerSecond() {
        return List.copyOf(messageCounterHistorySeconds);
    }

    /// Returns an immutable list of the message rate per minute over time.
    /// Each entry in the list is a [RateTuple] containing the count of messages
    /// for a specific minute and the timestamp when it was recorded.
    ///
    /// @return a [List] of [RateTuple].
    public List<RateTuple> getMessageRatePerMinute() {
        return List.copyOf(messageCounterHistoryMinutes);
    }
}
