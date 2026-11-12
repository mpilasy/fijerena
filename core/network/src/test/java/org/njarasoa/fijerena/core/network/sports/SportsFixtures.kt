package org.njarasoa.fijerena.core.network.sports

import org.njarasoa.fijerena.core.network.fixture

/** A saved real answer of a sports source, under `src/test/resources/fixtures/sports/`. */
internal fun sportsFixture(name: String): String = fixture("sports/$name")

/** Epoch seconds of an ISO time with an offset. */
internal fun epoch(iso: String): Long =
    java.time.OffsetDateTime
        .parse(iso)
        .toEpochSecond()
