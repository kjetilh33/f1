package com.kinnovatio.f1.livetiming.source;


import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// Reads live timing records from DB.
///
public class DbDataFeed implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(DbDataFeed.class);

    private static final String japaneseGP = """
            SELECT id, category, is_streaming, message, message_timestamp
            FROM public.live_timing_messages
            where
            created_timestamp > '2026-03-29 04:00:00'
            and
            created_timestamp < '2026-03-29 07:15:00'
            order by 1 asc
            """;

    private static final String miamiGP = """
            SELECT id, category, is_streaming, message, message_timestamp
            FROM public.live_timing_messages
            where
            created_timestamp > '2026-05-03 15:59:00'
            and
            created_timestamp < '2026-05-03 19:30:00'
            order by 1 asc
            """;

    // Config parameters
    private final String jdbcUrl =
            ConfigProvider.getConfig().getValue("source.jdbc.url", String.class);
    private final String username =
            ConfigProvider.getConfig().getValue("source.jdbc.username", String.class);
    private final String password =
            ConfigProvider.getConfig().getValue("source.jdbc.password", String.class);
    private final String dbTable =
            ConfigProvider.getConfig().getValue("source.jdbc.table", String.class);

    private final AtomicBoolean run = new AtomicBoolean(false);
    private final AtomicInteger messageCounterPerSecond = new AtomicInteger(0);
    private final Consumer<LiveTimingRecord> consumer;
    private ScheduledExecutorService executorService = null;

    public DbDataFeed(Consumer<LiveTimingRecord> consumer) {
        this.consumer = consumer;
    }

    public void start() {
        if (run.get()) {
            LOG.warn("DbDataFeed is already running. Call close() before starting again.");
            return;
        }
        if (executorService != null) {
            executorService.shutdown();
        }

        run.set(true);
        executorService = Executors.newSingleThreadScheduledExecutor();
        LOG.info("Starting DbDataFeed...");
        Thread.startVirtualThread(this);

        // Start a task to track the message rate per second
        executorService.scheduleAtFixedRate(() -> {
            // Get the current count and reset the counter
            int currentCountSeconds = messageCounterPerSecond.getAndSet(0);
            LOG.info("Message rate per second: {}", currentCountSeconds);
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void close() {
        run.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
    }


    @Override
    public void run() {
        List<String> queryList = List.of(japaneseGP, miamiGP);
        String query = queryList.get(ThreadLocalRandom.current().nextInt(0, 2));
        LOG.info("Connecting to database...");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            LOG.info("Connected.");
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                LOG.info("Querying database...");
                stmt.setFetchSize(1000);
                ResultSet rs = stmt.executeQuery();

                Instant queryStart = Instant.now();
                Instant firstRecord = null;
                LOG.info("Start processing records...");
                while (run.get() && rs.next()) {
                    String category = rs.getString("category");
                    boolean isStreaming = rs.getBoolean("is_streaming");
                    String message = rs.getString("message");
                    Instant messageTimestamp =
                            rs.getObject("message_timestamp", OffsetDateTime.class).toInstant();
                    LiveTimingMessage liveTimingMessage = new LiveTimingMessage(category, message, messageTimestamp, isStreaming);

                    if (isStreaming) {
                        if (firstRecord == null) {
                            firstRecord = liveTimingMessage.timestamp();
                        }

                        // Check the timing, so we keep pace with the original message stream.
                        Duration queryDuration = Duration.between(queryStart, Instant.now());
                        Duration recordDuration = Duration.between(firstRecord, Instant.now());
                        if (queryDuration.compareTo(recordDuration) < 0) {
                            // we need to wait for the record to "catch up"
                            long sleepMillies = Math.max(2000, recordDuration.toMillis() - queryDuration.toMillis());
                            Thread.sleep(sleepMillies);
                        }

                        consumer.accept(liveTimingMessage);
                        messageCounterPerSecond.incrementAndGet();

                    } else {
                        consumer.accept(liveTimingMessage);
                        messageCounterPerSecond.incrementAndGet();
                    }
                }
                LOG.info("Reached the end of data set.");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}
