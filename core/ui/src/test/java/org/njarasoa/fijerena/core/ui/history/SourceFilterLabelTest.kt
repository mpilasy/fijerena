package org.njarasoa.fijerena.core.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

/** The search filter's Source row names its source in every mode: the badge comes after it, never in its place. */
class SourceFilterLabelTest {
    @Test
    fun theRowLabelIsTheSourceNameWithItsCount() {
        assertEquals("bears (64)", sourceFilterLabel("bears", 64))
    }
}
