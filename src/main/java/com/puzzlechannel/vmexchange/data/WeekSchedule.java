package com.puzzlechannel.vmexchange.data;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class WeekSchedule {
    public static String key(ZonedDateTime now, String dayName, int hour, int minute) {
        DayOfWeek day;
        try { day = DayOfWeek.valueOf(dayName.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { day = DayOfWeek.MONDAY; }
        LocalDate boundary = now.toLocalDate().with(TemporalAdjusters.previousOrSame(day));
        LocalDateTime reset = LocalDateTime.of(boundary, LocalTime.of(hour, minute));
        if (now.toLocalDateTime().isBefore(reset)) boundary = boundary.minusWeeks(1);
        return boundary + "@" + day;
    }
    private WeekSchedule() {}
}
