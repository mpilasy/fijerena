package org.njarasoa.fijerena.core.network.xmltv

import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer

/**
 * Keeps a guide source's programme times in the guide index in step with its offset. A changed
 * offset moves the times already there, at once: a refresh would not, as an unchanged file is not
 * imported again.
 *
 * The times are read with the source's own offset when it has one, else with the offset written in
 * the file ([EpgSourceEntity.fileUtcOffsetMinutes]). [EpgSourceEntity.appliedUtcOffsetMinutes] is the
 * offset the times in the index were read with. When one of the two is not known (a file with
 * times in more than one offset, or a source imported before this was kept), the times cannot be
 * moved: the source's validators are dropped, so its next refresh imports it again.
 */
object EpgOffsetShift {
    enum class Result {
        /** The times are already read with the right offset, or the source has none in the index. */
        IN_STEP,

        /** The times were moved. */
        SHIFTED,

        /** The times could not be moved: the next refresh imports the source again. */
        NEEDS_IMPORT,
    }

    /**
     * The offset, in minutes, times are read with for a source with [timezoneOffsetHours] whose
     * file has [fileUtcOffsetMinutes]; null when not known.
     */
    fun readMinutes(
        timezoneOffsetHours: Int,
        fileUtcOffsetMinutes: Int?,
    ): Int? = if (timezoneOffsetHours != 0) timezoneOffsetHours * 60 else fileUtcOffsetMinutes

    /** Brings [sourceId]'s times in the index in step with its offset. Call after any change of the offset, and after an import. */
    suspend fun reconcile(
        sourceId: Long,
        sourceDao: EpgSourceDao,
        indexer: EpgIndexer,
    ): Result {
        val source = sourceDao.getSourceById(sourceId) ?: return Result.IN_STEP
        // Nothing in the index yet: the next import reads the times with the offset.
        if (source.lastIngestedAtMs == 0L) return Result.IN_STEP
        val target = readMinutes(source.timezoneOffsetHours, source.fileUtcOffsetMinutes)
        val applied = source.appliedUtcOffsetMinutes
        if (applied == target) return Result.IN_STEP
        if (applied == null || target == null) {
            sourceDao.forgetValidators(sourceId)
            return Result.NEEDS_IMPORT
        }
        // A time read with offset A is (wall clock - A); read with offset T it is (wall clock - T).
        indexer.shiftSourceTimes(sourceId, (applied - target) * 60L)
        sourceDao.setAppliedOffset(sourceId, target)
        return Result.SHIFTED
    }
}
