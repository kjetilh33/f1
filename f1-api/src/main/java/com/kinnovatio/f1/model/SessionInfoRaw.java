package com.kinnovatio.f1.model;

public record SessionInfoRaw(int key, String name, String type, String number, String endDate, Meeting meeting,
                             String gmtOffset, String startDate, ArchiveStatus archiveStatus, String sessionStatus) {
}
