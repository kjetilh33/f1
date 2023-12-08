package com.kinnovatio;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import jakarta.websocket.Session;

public class LiveDataFeed implements Runnable {
    private static final Path practicePath = Path.of("/data/received-messages-practice3.log");
    private static final Path qualifyingPath = Path.of("/data/received-messages-qualifying.log");
    private static final Path racePath = Path.of("/data/received-messages-race.log");

    private static final String resourceLogFile = "/received-messages-race-short.log";

    private final Logger LOG = Logger.getLogger(this.getClass());
    private final Session session;
    private AtomicBoolean run = new AtomicBoolean(false);

    LiveDataFeed(Session session) {
        this.session = session;
    }

    public void start() {
        run.set(true);
        Thread.startVirtualThread(this);
    }

    public void close() {
        run.set(false);
    }

    @Override
    public void run() {
        try (BufferedReader reader = Files.newBufferedReader(racePath, StandardCharsets.UTF_8)) {
            while (run.get()) {
                session.getAsyncRemote().sendText(reader.readLine());
                Thread.sleep(500);
            }

        } catch (Exception e) {
            LOG.warnf("Error while reading message file: %s", e.toString());
        }        
    }

    private static Path getFilePath() {
        List<Path> pathList = List.of(practicePath, qualifyingPath, racePath);
        Path filePath = pathList.get(ThreadLocalRandom.current().nextInt(0, 3));

    }
}