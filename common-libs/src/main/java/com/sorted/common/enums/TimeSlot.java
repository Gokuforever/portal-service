package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents time slots in quarters of the day.
 */
@Getter
@AllArgsConstructor
public enum TimeSlot {
    MORNING("09:00-12:00", "Morning"),
    AFTERNOON("12:00-15:00", "Afternoon"),
    EVENING("15:00-18:00", "Evening"),
    NIGHT("18:00-21:00", "Night");

    private final String timeRange;
    private final String displayName;

    /**
     * Gets the display name of the time slot.
     * @return The display name of the time slot
     */
    @Override
    public String toString() {
        return this.displayName;
    }

    public static TimeSlot getCurrentTimeSlot() {
        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (TimeSlot slot : TimeSlot.values()) {
            String[] parts = slot.getTimeRange().split("-");
            LocalTime start = LocalTime.parse(parts[0], formatter);
            LocalTime end = LocalTime.parse(parts[1], formatter);

            if (!now.isBefore(start) && now.isBefore(end)) {
                return slot;
            }
        }

        return null;
    }
}
