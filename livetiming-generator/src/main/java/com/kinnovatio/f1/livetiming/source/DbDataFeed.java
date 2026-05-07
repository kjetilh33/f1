package com.kinnovatio.f1.livetiming.source;


import com.kinnovatio.signalr.messages.LiveTimingMessage;
import com.kinnovatio.signalr.messages.LiveTimingRecord;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Consumer<LiveTimingRecord> consumer;

    public DbDataFeed(Consumer<LiveTimingRecord> consumer) {
        this.consumer = consumer;
    }

    public void start() {
        run.set(true);
        Thread.startVirtualThread(this);
    }

    @Override
    public void run() {
        List<String> queryList = List.of(japaneseGP, miamiGP);
        String query = queryList.get(ThreadLocalRandom.current().nextInt(0, 2));

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setFetchSize(1000);
                ResultSet rs = stmt.executeQuery();

                Instant queryStart = Instant.now();
                Instant firstRecord = null;
                while (run.get() && rs.next()) {
                    String category = rs.getString("category");
                    boolean isStreaming = rs.getBoolean("is_streaming");
                    String message = rs.getString("message");
                    Instant messageTimestamp =
                            rs.getObject("message_timestamp", OffsetDateTime.class).toInstant();
                    LiveTimingMessage liveTimingMessage = new LiveTimingMessage(category, message, messageTimestamp, isStreaming);

                    if (isStreaming) {
                        if (firstRecord == null) {
                            firstRecord = messageTimestamp;
                        }
                    }



                }


            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}
