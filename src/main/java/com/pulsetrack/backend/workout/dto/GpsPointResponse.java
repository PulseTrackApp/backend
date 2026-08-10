package com.pulsetrack.backend.workout.dto;

import java.time.Instant;

public record GpsPointResponse(
        int position,
        double latitude,
        double longitude,
        Double altitude,
        Double accuracy,
        Double speed,
        Instant recordedAt) {
}
