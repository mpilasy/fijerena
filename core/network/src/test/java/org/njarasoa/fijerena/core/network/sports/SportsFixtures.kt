package org.njarasoa.fijerena.core.network.sports

/** A saved real answer of a sports source, under `src/test/resources/sports/`. */
internal fun sportsFixture(name: String): String =
    requireNotNull(SportsSource::class.java.classLoader?.getResource("sports/$name")) { "No fixture $name" }.readText()

/** Epoch seconds of an ISO time with an offset. */
internal fun epoch(iso: String): Long =
    java.time.OffsetDateTime
        .parse(iso)
        .toEpochSecond()
