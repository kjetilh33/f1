package com.kinnovatio.f1.livetiming;

/// Encapsulates information about a Formula 1 session extracted from live timing data.
///
/// Contains details such as meeting name, session status, event type, scheduled start/end dates,
/// and archive status.
///
/// @param meetingName The name of the Grand Prix or event meeting (e.g., "Bahrain Grand Prix").
/// @param status The current status of the session (e.g., "Started", "Finished").
/// @param type The type of the session (e.g., "Practice 1", "Qualifying", "Race").
/// @param startDate The scheduled start date and time as a string.
/// @param endDate The scheduled end date and time as a string.
/// @param archiveStatus The archival processing status (e.g., "Generating", "Complete").
public record SessionInfo(String meetingName, String status, String type, String startDate, String endDate, String archiveStatus) {}
