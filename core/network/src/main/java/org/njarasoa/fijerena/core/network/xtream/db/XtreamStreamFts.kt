package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(contentEntity = XtreamStreamEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "xtream_streams_fts")
data class XtreamStreamFts(
    val name: String,
)
