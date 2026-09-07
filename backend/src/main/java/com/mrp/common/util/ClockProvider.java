package com.mrp.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class ClockProvider {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static volatile Clock clock = Clock.system(ZONE_SHANGHAI);

    private ClockProvider() {}

    public static Clock clock() {
        return clock;
    }

    public static Instant now() {
        return Instant.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static ZoneId zone() {
        return ZONE_SHANGHAI;
    }

    public static void useFixedClock(Instant fixedInstant) {
        clock = Clock.fixed(fixedInstant, ZONE_SHANGHAI);
    }

    public static void resetClock() {
        clock = Clock.system(ZONE_SHANGHAI);
    }
}
