package org.njarasoa.fijerena.core.player.display

import java.util.Locale

/** A display mode, without `Display.Mode`, so that the code that uses it can be tested on the JVM. */
data class ModeSpec(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/** A rate with up to three decimals and no zeros at the end: "23.976", "24", "59.94". */
fun rateText(rate: Float): String = String.format(Locale.US, "%.3f", rate).trimEnd('0').trimEnd('.')

/** The mode as Stats for Nerds shows it: "3840×2160 · 23.976 Hz". */
fun displayModeText(mode: ModeSpec): String = "${mode.width}×${mode.height} · ${rateText(mode.refreshRate)} Hz"

/** The refresh rates of the modes at [current]'s resolution, low to high: "23.976, 24, 50, 59.94, 60 Hz". */
fun refreshRatesText(
    modes: List<ModeSpec>,
    current: ModeSpec,
): String =
    (modes + current)
        .filter { it.width == current.width && it.height == current.height }
        .map { it.refreshRate }
        .sorted()
        .map(::rateText)
        .distinct()
        .joinToString(", ", postfix = " Hz")
