package com.kinnovatio.livetiming;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.Statement;

@ApplicationScoped
public class AppLifeCycleObserver {
    private static final Logger LOG = Logger.getLogger(AppLifeCycleObserver.class);

    @Inject
    AgroalDataSource storageDataSource;

    @ConfigProperty(name = "app.log.source")
    String logSurce;

    @ConfigProperty(name = "app.livetiming.table")
    String livetimingTable;

    @ConfigProperty(name = "app.session-info.table")
    String sessionInfoTable;


    void onStart(@Observes StartupEvent ev) {
        // This runs when the application is starting.
        LOG.infof("Starting the live timing processor...");
        LOG.infof("Config picked up from %s", logSurce);

        createLiveTimingDbTableIfNotExists(livetimingTable);
        createSessionInfoDbTableIfNotExists(sessionInfoTable);

        LOG.infof("The processor is ready. Waiting for live timing messages...");
    }

    void onStop(@Observes ShutdownEvent ev) {
        // Cleanup logic
    }

    /// Creates the database table and indexes if they do not already exist.
    /// The table `live_timing_messages` stores the raw JSON message, timestamp, category,
    /// and a hash of the message content.
    private void createLiveTimingDbTableIfNotExists(String tableName) {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    message_id SERIAL PRIMARY KEY,
                    category VARCHAR(100) DEFAULT 'N/A',
                    is_streaming BOOLEAN DEFAULT FALSE,
                    message JSONB,
                    message_timestamp TIMESTAMPTZ,
                    message_hash TEXT,
                    created_timestamp TIMESTAMPTZ DEFAULT NOW()
                );
                """.formatted(tableName);

        String createIndexStatement = """
                CREATE INDEX IF NOT EXISTS idx_category_timestamp ON %s (category, message_timestamp);
                CREATE INDEX IF NOT EXISTS idx_hash ON %s (message_hash);
                """.formatted(tableName, tableName);

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            statement.execute(createIndexStatement);
            LOG.infof("Successfully created (if not already exists) the livetiming DB table...");
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s", e.getMessage());
        }
    }

    private void createSessionInfoDbTableIfNotExists(String tableName) {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS %s (
                    key VARCHAR(100) PRIMARY KEY,
                    message JSONB
                );
                """.formatted(tableName);

        try (Connection connection = storageDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            LOG.infof("Successfully created (if not already exists) the session info DB table...");
        } catch (Exception e) {
            LOG.errorf("An error happened when creating the DB table: %s", e.getMessage());
        }
    }
}
