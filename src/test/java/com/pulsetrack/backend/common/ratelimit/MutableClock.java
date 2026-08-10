package com.pulsetrack.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Horloge que le test avance a la main.
 *
 * <p>Indispensable pour eprouver une limite exprimee en minutes : attendre
 * reellement l'expiration d'une fenetre rendrait la suite interminable, et
 * raccourcir les fenetres pour les besoins du test reviendrait a ne pas tester
 * les durees reelles.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advanceBy(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new MutableClock(instant, otherZone);
    }
}
