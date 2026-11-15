package com.astronaut.backend.reports.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant instant) { this.instant = instant; }

    public void set(Instant instant) { this.instant = instant; }
    public void advance(Duration d) { this.instant = this.instant.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) {
        MutableClock self = this;
        return new Clock() {
            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId z) { return self.withZone(z); }
            @Override public Instant instant() { return self.instant(); }
        };
    }
    @Override public Instant instant() { return instant; }
}
