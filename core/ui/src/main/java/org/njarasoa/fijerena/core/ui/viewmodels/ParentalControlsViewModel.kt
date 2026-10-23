package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ParentalControlsRepository
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * Settings › Parental controls (docs/plans/20261020_parental-controls-plan.md), for TV and phone:
 * the five lists, each entry's match count on the child profiles' sources, and the check view of
 * one source. Every change is stored, sent to the other devices and applied at once.
 */
class ParentalControlsViewModel(
    context: Context,
) : ViewModel() {
    private val appSettings = AppSettings(context)
    private val providers = ProviderRepository(context)
    private val repository = ParentalControlsRepository(context)

    private val _lists = MutableStateFlow(readLists())

    /** Each list's entries, in the order they were added. */
    val lists: StateFlow<Map<ParentalList, List<String>>> = _lists.asStateFlow()

    private val _counts = MutableStateFlow<Map<Pair<ParentalList, String>, Int>>(emptyMap())

    /** Match counts loaded by [loadCounts], by (list, entry); missing while loading. */
    val counts: StateFlow<Map<Pair<ParentalList, String>, Int>> = _counts.asStateFlow()

    private val _sources = MutableStateFlow<List<ParentalControlsRepository.Source>>(emptyList())

    /** The sources the counts and the check view look at: the child profiles' (all, with no child profile). */
    val sources: StateFlow<List<ParentalControlsRepository.Source>> = _sources.asStateFlow()

    private val _check = MutableStateFlow<List<ParentalControlsRepository.CheckedCategory>?>(null)

    /** The check view's categories for the source [loadCheck] was last given; null while loading. */
    val check: StateFlow<List<ParentalControlsRepository.CheckedCategory>?> = _check.asStateFlow()

    init {
        viewModelScope.launchGuarded("parental sources") { _sources.value = repository.sources() }
    }

    /** The lists as stored now, after a change from another device too. */
    fun refresh() {
        _lists.value = readLists()
    }

    /** Adds [entry] to [list] (trimmed; nothing when blank or there already, in any case). */
    fun add(
        list: ParentalList,
        entry: String,
    ) {
        val clean = entry.trim()
        val current = _lists.value[list].orEmpty()
        if (clean.isEmpty() || current.any { it.equals(clean, ignoreCase = true) }) return
        save(list, current + clean)
        loadCount(list, clean)
    }

    /** Takes [entry] out of [list]; [undo] puts it back at the same place. */
    fun remove(
        list: ParentalList,
        entry: String,
    ) {
        save(list, _lists.value[list].orEmpty() - entry)
    }

    /** Puts [entry] back in [list] at [index], after [remove]. */
    fun undo(
        list: ParentalList,
        entry: String,
        index: Int,
    ) {
        val current = _lists.value[list].orEmpty()
        if (entry in current) return
        save(list, current.toMutableList().apply { add(index.coerceIn(0, size), entry) })
    }

    /** [list] becomes the app's words again; the admin's changes to it are gone. */
    fun reset(list: ParentalList) {
        save(list, list.defaults)
        loadCounts(list)
    }

    /** Loads the match count of every entry of [list]. */
    fun loadCounts(list: ParentalList) {
        _lists.value[list].orEmpty().forEach { loadCount(list, it) }
    }

    /** Loads the check view for [providerId]. */
    fun loadCheck(providerId: Long) {
        _check.value = null
        viewModelScope.launchGuarded("parental check") { _check.value = repository.check(providerId) }
    }

    private fun loadCount(
        list: ParentalList,
        entry: String,
    ) {
        viewModelScope.launchGuarded("parental count") {
            val sources = _sources.value.ifEmpty { repository.sources().also { _sources.value = it } }
            val count = repository.matchCount(list, entry, sources)
            _counts.update { it + ((list to entry) to count) }
        }
    }

    private fun save(
        list: ParentalList,
        entries: List<String>,
    ) {
        _lists.update { it + (list to entries) }
        viewModelScope.launchGuarded("parental save") { providers.setParentalList(list, entries) }
    }

    private fun readLists(): Map<ParentalList, List<String>> = ParentalList.entries.associateWith { appSettings.parentalList(it) }
}
