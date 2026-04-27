package com.obrynex.studyguard.islamic.ui

import androidx.lifecycle.ViewModel
import com.obrynex.studyguard.islamic.data.ALL_HADITHS
import com.obrynex.studyguard.islamic.data.Hadith
import com.obrynex.studyguard.islamic.data.HadithCategory
import com.obrynex.studyguard.islamic.data.dailyHadith
import com.obrynex.studyguard.islamic.data.nawawiHadiths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the Islamic tab.
 *
 * @param hadiths          Filtered list shown in the LazyColumn.
 * @param selectedCategory Active category chip, null means "الكل".
 * @param searchQuery      Live search text.
 * @param dailyHadith      Today's featured hadith — changes once per day.
 * @param bookmarkedIds    Set of hadith IDs the user has bookmarked (in-memory).
 * @param showNawawiOnly   When true, list is restricted to the Forty Nawawi hadiths.
 */
data class IslamicUiState(
    val hadiths          : List<Hadith>    = ALL_HADITHS,
    val selectedCategory : HadithCategory? = null,
    val searchQuery      : String          = "",
    val dailyHadith      : Hadith          = dailyHadith(),
    val bookmarkedIds    : Set<Int>        = emptySet(),
    val showNawawiOnly   : Boolean         = false
)

class IslamicViewModel : ViewModel() {

    private val _state = MutableStateFlow(IslamicUiState())
    val state: StateFlow<IslamicUiState> = _state.asStateFlow()

    // ── Filtering ──────────────────────────────────────────────────────────

    fun onCategorySelected(category: HadithCategory?) {
        _state.update { current ->
            current.copy(
                selectedCategory = category,
                showNawawiOnly   = false,
                hadiths          = applyFilters(
                    query        = current.searchQuery,
                    category     = category,
                    nawawiOnly   = false
                )
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { current ->
            current.copy(
                searchQuery = query,
                hadiths     = applyFilters(
                    query      = query,
                    category   = current.selectedCategory,
                    nawawiOnly = current.showNawawiOnly
                )
            )
        }
    }

    fun toggleNawawiFilter() {
        _state.update { current ->
            val newNawawi = !current.showNawawiOnly
            current.copy(
                showNawawiOnly   = newNawawi,
                selectedCategory = null,
                hadiths          = applyFilters(
                    query      = current.searchQuery,
                    category   = null,
                    nawawiOnly = newNawawi
                )
            )
        }
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    fun toggleBookmark(hadithId: Int) {
        _state.update { current ->
            val updated = if (hadithId in current.bookmarkedIds)
                current.bookmarkedIds - hadithId
            else
                current.bookmarkedIds + hadithId
            current.copy(bookmarkedIds = updated)
        }
    }

    fun isBookmarked(hadithId: Int): Boolean =
        _state.value.bookmarkedIds.contains(hadithId)

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun applyFilters(
        query      : String,
        category   : HadithCategory?,
        nawawiOnly : Boolean
    ): List<Hadith> {
        var base = if (nawawiOnly) nawawiHadiths() else ALL_HADITHS

        if (category != null) {
            base = base.filter { it.category == category }
        }

        if (query.isNotBlank()) {
            base = base.filter { hadith ->
                hadith.text.contains(query, ignoreCase = true) ||
                hadith.benefit.contains(query, ignoreCase = true) ||
                hadith.narrator.contains(query, ignoreCase = true) ||
                hadith.source.contains(query, ignoreCase = true)
            }
        }

        return base
    }
}
