package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(contentEntity = XtreamSeriesEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "xtream_series_fts")
data class XtreamSeriesFts(
    val name: String,
)
