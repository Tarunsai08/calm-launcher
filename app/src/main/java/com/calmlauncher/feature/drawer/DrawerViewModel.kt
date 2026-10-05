package com.calmlauncher.feature.drawer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.calc.Calculator
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.domain.search.SearchDocument
import com.calmlauncher.domain.search.SearchEngine
import com.calmlauncher.domain.search.SearchIndex
import com.calmlauncher.domain.search.SearchOptions
import com.calmlauncher.domain.search.UsageScore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DrawerUiState(
    val query: String = "",
    val items: List<DrawerItem> = emptyList(),
    /** Set when exactly one app matches a non-empty query (auto-launch candidate). */
    val singleMatch: LauncherApp? = null,
    val letters: List<Pair<String, Int>> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategory: Long? = null,
    val searchMillis: Long = 0,
)

private data class IndexBundle(
    val apps: List<LauncherApp>,
    val byId: Map<String, LauncherApp>,
    val appIndex: SearchIndex,
    val shortcuts: List<Pair<SearchShortcut, String>>,
    val shortcutIndex: SearchIndex,
    val duplicateLabels: Set<String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    apps: AppsRepository,
    rules: RulesRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val category = MutableStateFlow(savedState.get<Long>(KEY_CATEGORY))

    /** Search indexes are rebuilt off the main thread only when apps or relevant settings change. */
    private val index: StateFlow<IndexBundle?> = combine(
        apps.apps,
        settingsRepository.settings.map { Triple(it.notesEnabled, it.todosEnabled, it.timerShortcutEnabled) }.distinctUntilChanged(),
    ) { list, tools ->
        buildIndex(list, tools.first, tools.second, tools.third)
    }.flowOn(Dispatchers.Default).stateIn<IndexBundle?>(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DrawerUiState> = combine(
        query,
        index,
        settingsRepository.settings,
        category,
        rules.categories,
    ) { q, idx, s, cat, cats -> Inputs(q, idx, s, cat, cats) }
        .mapLatest { compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState(query = query.value))

    private data class Inputs(
        val query: String,
        val index: IndexBundle?,
        val settings: LauncherSettings,
        val category: Long?,
        val categories: List<CategoryEntity>,
    )

    fun setQuery(value: String) {
        query.value = value
        savedState[KEY_QUERY] = value
    }

    fun selectCategory(id: Long?) {
        category.value = id
        savedState[KEY_CATEGORY] = id
    }

    fun clear() = setQuery("")

    private fun buildIndex(list: List<LauncherApp>, notes: Boolean, todos: Boolean, timer: Boolean): IndexBundle {
        val now = System.currentTimeMillis()
        val docs = list.map { app ->
            SearchDocument(
                id = app.key.id,
                label = app.label,
                aliases = listOfNotNull(app.systemLabel.takeIf { app.alias != null && it.isNotBlank() }),
                packageName = app.key.packageName,
                hidden = app.hidden,
                usageScore = UsageScore.compute(app.launchCount, app.lastLaunchedAt, now),
            )
        }
        val shortcuts = SearchShortcuts.all(notes, todos, timer).map { it to context.getString(it.labelRes) }
        val shortcutDocs = shortcuts.map { (sc, label) ->
            SearchDocument(id = sc.id, label = label, aliases = sc.keywords)
        }
        val labelCounts = list.filter { !it.hidden }.groupingBy { it.label.lowercase() }.eachCount()
        return IndexBundle(
            apps = list,
            byId = list.associateBy { it.key.id },
            appIndex = SearchIndex(docs),
            shortcuts = shortcuts,
            shortcutIndex = SearchIndex(shortcutDocs),
            duplicateLabels = labelCounts.filterValues { it > 1 }.keys,
        )
    }

    private fun compute(input: Inputs): DrawerUiState {
        val idx = input.index ?: return DrawerUiState(query = input.query, categories = input.categories)
        val s = input.settings
        val q = input.query.trim()
        val start = System.nanoTime()
        if (q.isEmpty()) {
            val visible = idx.apps.filter { !it.hidden && (input.category == null || it.categoryId == input.category) }
            val items = ArrayList<DrawerItem>(visible.size + 26)
            val letters = ArrayList<Pair<String, Int>>()
            var lastLetter: String? = null
            for (app in visible) {
                val letter = sectionLetter(app.label)
                if (letter != lastLetter) {
                    letters += letter to items.size
                    lastLetter = letter
                }
                items += DrawerItem.App(app, hintFor(app, idx))
            }
            return DrawerUiState(
                query = input.query,
                items = items,
                letters = letters,
                categories = if (s.showCategories) input.categories else emptyList(),
                selectedCategory = input.category,
                searchMillis = (System.nanoTime() - start) / 1_000_000,
            )
        }

        val options = SearchOptions(matchPackageNames = s.searchPackageNames, hiddenSearchable = s.hiddenSearchable)
        val appHits = SearchEngine.search(idx.appIndex, q, options)
        val items = ArrayList<DrawerItem>()
        if (s.searchCalculator) {
            Calculator.evaluate(q)?.let { items += DrawerItem.Calculation(q, Calculator.format(it)) }
        }
        val appItems = appHits.mapNotNull { hit -> idx.byId[hit.document.id]?.let { DrawerItem.App(it, hintFor(it, idx)) } }
        items += appItems
        if (s.searchSettingsShortcuts) {
            val scHits = SearchEngine.search(idx.shortcutIndex, q, SearchOptions(limit = 4))
            for (hit in scHits) {
                val pair = idx.shortcuts.firstOrNull { it.first.id == hit.document.id } ?: continue
                items += DrawerItem.Shortcut(pair.first, pair.second)
            }
        }
        if (s.searchContacts && q.length >= 2) {
            ContactSearch.search(context, q).forEach { items += DrawerItem.Contact(it.name, it.uri) }
        }
        if (s.webSearchFallback) items += DrawerItem.WebSearch(q)
        val single = appItems.singleOrNull()?.app
        return DrawerUiState(
            query = input.query,
            items = items,
            singleMatch = single,
            searchMillis = (System.nanoTime() - start) / 1_000_000,
        )
    }

    private fun hintFor(app: LauncherApp, idx: IndexBundle): String? =
        if (app.label.lowercase() in idx.duplicateLabels && !app.isWorkProfile && !app.isClone) {
            app.key.packageName.substringAfterLast('.')
        } else {
            null
        }

    companion object {
        private const val KEY_QUERY = "drawer_query"
        private const val KEY_CATEGORY = "drawer_category"

        fun sectionLetter(label: String): String {
            val c = label.trim().firstOrNull() ?: return "#"
            val normalized = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).firstOrNull() ?: c
            return if (normalized.isLetter()) normalized.uppercaseChar().toString() else "#"
        }
    }
}
