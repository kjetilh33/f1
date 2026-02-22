package com.kinnovatio.f1.livetiming.source;

import com.kinnovatio.signalr.messages.LiveTimingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FileDataFeed implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(FileDataFeed.class);
    private static final Path practicePath = Path.of("/data/received-messages-practice3.log");
    private static final Path qualifyingPath = Path.of("/data/received-messages-qualifying.log");
    private static final Path racePath = Path.of("/data/received-messages-race.log");
    private static final Path raceImolaPath = Path.of("/data/2025-050-18-italy-imola-race-received-messages.log");

    private static final String resourceLogFile = "/received-messages-race-short.log";

    private final AtomicBoolean run = new AtomicBoolean(false);
    private final Consumer<LiveTimingRecord> consumer;

    public FileDataFeed(Consumer<LiveTimingRecord> consumer) {
        this.consumer = consumer;
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
        List<? extends LiveTimingRecord> messages;
        try (BufferedReader reader = Files.newBufferedReader(getFilePath(), StandardCharsets.UTF_8)) {
            String line;
            while (run.get() && (line = reader.readLine()) != null) {
                messages = Parser.parseSignalRMessage(line);
                messages.forEach(message -> consumer.accept(message));

                Thread.sleep(300);
            }

        } catch (Exception e) {
            LOG.warn("Error while reading message file: %s", e.toString());
        }        
    }

    private Path getFilePath() throws URISyntaxException, IOException {
        List<Path> pathList = List.of(practicePath, racePath, racePath, raceImolaPath);
        Path filePath = pathList.get(ThreadLocalRandom.current().nextInt(0, 4));
        if (!Files.exists(filePath)) {
            LOG.warn("Unable to read file %s. Will use bundled file instead.", filePath);
            filePath = Path.of(this.getClass().getResource(resourceLogFile).toURI());
        }

        return filePath;
    }
}