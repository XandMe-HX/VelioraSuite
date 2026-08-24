package id.velioragardens.veliorasuite.module.trader;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

final class TraderSchedule {

    private TraderSchedule() {
    }

    static long nextSpawn(long nowMillis, ZoneId zone, int intervalHours, int intervalDays, DayOfWeek scheduledDay, int anchorHour, int activeMinutes, boolean includeActiveWindow) {
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        int safeInterval = Math.max(1, intervalHours);
        int safeAnchor = Math.floorMod(anchorHour, 24);
        ZonedDateTime slot;

        if (intervalDays > 0 && scheduledDay != null) {
            int daysSinceScheduledDay = Math.floorMod(now.getDayOfWeek().getValue() - scheduledDay.getValue(), 7);
            slot = now.truncatedTo(ChronoUnit.DAYS).minusDays(daysSinceScheduledDay).plusHours(safeAnchor);
            if (slot.isAfter(now)) slot = slot.minusDays(7);
            int safeDays = Math.max(1, intervalDays);
            while (!slot.plusDays(safeDays).isAfter(now)) slot = slot.plusDays(safeDays);
            if (includeActiveWindow && now.isBefore(slot.plusMinutes(Math.max(1, activeMinutes)))) return nowMillis;
            return slot.plusDays(safeDays).toInstant().toEpochMilli();
        }

        slot = now.truncatedTo(ChronoUnit.DAYS).plusHours(safeAnchor);

        while (slot.isAfter(now)) slot = slot.minusHours(safeInterval);
        while (!slot.plusHours(safeInterval).isAfter(now)) slot = slot.plusHours(safeInterval);

        if (includeActiveWindow && now.isBefore(slot.plusMinutes(Math.max(1, activeMinutes)))) {
            return nowMillis;
        }
        return slot.plusHours(safeInterval).toInstant().toEpochMilli();
    }
}
