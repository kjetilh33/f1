package com.kinnovatio;

import jakarta.enterprise.context.SessionScoped;

import java.nio.file.Path;

@SessionScoped
public class LiveDataFeed {
    private static final Path practicePath = Path.of("/data/received-messages-practice3.log");
    private static final Path qualifyingPath = Path.of("/data/received-messages-qualifying.log");
    private static final Path racePath = Path.of("/data/received-messages-race.log");
}
