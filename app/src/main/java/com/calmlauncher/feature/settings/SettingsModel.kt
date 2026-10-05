package com.calmlauncher.feature.settings

/**
 * Declarative settings rows. Screens are built from these so the whole settings tree can be
 * searched by title and description.
 */
sealed interface SettingItem {
    val id: String
    val title: String
    val description: String?

    data class Toggle(
        override val id: String,
        override val title: String,
        override val description: String?,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onChange: (Boolean) -> Unit,
    ) : SettingItem

    data class Action(
        override val id: String,
        override val title: String,
        override val description: String?,
        val value: String? = null,
        val onClick: () -> Unit,
    ) : SettingItem

    data class Choice(
        override val id: String,
        override val title: String,
        override val description: String?,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : SettingItem

    data class Slider(
        override val id: String,
        override val title: String,
        override val description: String?,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val format: (Float) -> String,
        val onChange: (Float) -> Unit,
    ) : SettingItem
}

data class SettingsSection(
    val id: String,
    val title: String,
    val items: List<SettingItem>,
)

fun List<SettingsSection>.search(query: String): List<Pair<SettingsSection, SettingItem>> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return flatMap { section ->
        section.items.filter {
            it.title.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) ?: false) ||
                section.title.lowercase().contains(q)
        }.map { section to it }
    }
}
