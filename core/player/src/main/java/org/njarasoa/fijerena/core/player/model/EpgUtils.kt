package org.njarasoa.fijerena.core.player.model

/**
 * EPG-specific utility functions.
 */
object EpgUtils {
    fun isCurrentProgram(program: EpgProgram): Boolean {
        val now = System.currentTimeMillis() / 1000
        return now in program.startTime..program.endTime
    }
}
