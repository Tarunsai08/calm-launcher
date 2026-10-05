package com.calmlauncher.data.rules

import com.calmlauncher.data.db.CategoryDao
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.db.LimitExtensionEntity
import com.calmlauncher.data.db.RuleDao
import com.calmlauncher.data.db.RuleEntity
import com.calmlauncher.data.db.TimeWindowEntity
import com.calmlauncher.data.db.AppMetaDao
import com.calmlauncher.domain.focus.FocusRule
import com.calmlauncher.domain.focus.LimitExtension
import com.calmlauncher.domain.focus.RuleTarget
import com.calmlauncher.domain.focus.TimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class FocusScheduleUi(
    val id: Long,
    val name: String,
    val window: TimeWindow,
    val enabled: Boolean,
)

/** Snapshot of every rule, kept hot in memory so launch decisions never hit the disk. */
data class RulesSnapshot(
    val rules: Map<String, FocusRule> = emptyMap(),
    val focusSchedules: List<FocusScheduleUi> = emptyList(),
    val windowsByOwner: Map<String, List<TimeWindowEntity>> = emptyMap(),
) {
    fun ruleFor(target: RuleTarget): FocusRule? = rules[target.storageKey]

    val hasAnyRules: Boolean get() = rules.values.any { !it.isEmpty }
}

class RulesRepository(
    private val ruleDao: RuleDao,
    private val categoryDao: CategoryDao,
    private val metaDao: AppMetaDao,
    scope: CoroutineScope,
) {
    val snapshot: StateFlow<RulesSnapshot> = combine(ruleDao.observeRules(), ruleDao.observeWindows()) { rules, windows ->
        val byOwner = windows.groupBy { it.owner }
        val ruleMap = LinkedHashMap<String, FocusRule>()
        val targets = (rules.map { it.target } + byOwner.keys.filter { it != TimeWindowEntity.OWNER_FOCUS }).toSet()
        for (target in targets) {
            val parsed = RuleTarget.parse(target) ?: continue
            val entity = rules.firstOrNull { it.target == target }
            val ruleWindows = byOwner[target].orEmpty().filter { it.enabled }.mapNotNull { it.toWindowOrNull() }
            ruleMap[target] = FocusRule(
                target = parsed,
                pauseEnabled = entity?.pauseEnabled ?: false,
                pauseSeconds = entity?.pauseSeconds ?: FocusRule.DEFAULT_PAUSE_SECONDS,
                askReason = entity?.askReason ?: true,
                dailyLimitMinutes = entity?.dailyLimitMinutes,
                alwaysBlocked = entity?.alwaysBlocked ?: false,
                blockWindows = ruleWindows,
            )
        }
        val schedules = byOwner[TimeWindowEntity.OWNER_FOCUS].orEmpty().mapNotNull { e ->
            e.toWindowOrNull()?.let { FocusScheduleUi(e.id, e.name, it, e.enabled) }
        }
        RulesSnapshot(ruleMap, schedules, byOwner)
    }.stateIn(scope, SharingStarted.Eagerly, RulesSnapshot())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val todayExtensions: StateFlow<Map<String, LimitExtension>> =
        ruleDao.observeExtensions(LocalDate.now().toEpochDay())
            .map { list -> list.associate { it.target to LimitExtension(it.extraMinutes, it.unlimited) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun saveRule(rule: FocusRule) {
        val key = rule.target.storageKey
        if (rule.isEmpty && rule.blockWindows.isEmpty()) {
            ruleDao.deleteRule(key)
        } else {
            ruleDao.upsertRule(
                RuleEntity(
                    target = key,
                    pauseEnabled = rule.pauseEnabled,
                    pauseSeconds = rule.pauseSeconds,
                    askReason = rule.askReason,
                    dailyLimitMinutes = rule.dailyLimitMinutes,
                    alwaysBlocked = rule.alwaysBlocked,
                ),
            )
        }
    }

    suspend fun clearRule(target: RuleTarget) {
        ruleDao.deleteRule(target.storageKey)
        ruleDao.deleteWindowsFor(target.storageKey)
    }

    suspend fun saveWindow(entity: TimeWindowEntity): Long = ruleDao.upsertWindow(entity)

    suspend fun deleteWindow(id: Long) = ruleDao.deleteWindow(id)

    /** Extensions are looked up fresh (not from the cached flow) since "today" may have rolled over. */
    suspend fun extensionFor(target: RuleTarget, day: LocalDate = LocalDate.now()): LimitExtension? =
        ruleDao.getExtension(target.storageKey, day.toEpochDay())?.let { LimitExtension(it.extraMinutes, it.unlimited) }

    suspend fun extend(target: RuleTarget, minutes: Int, day: LocalDate = LocalDate.now()) {
        val existing = ruleDao.getExtension(target.storageKey, day.toEpochDay())
        ruleDao.upsertExtension(
            LimitExtensionEntity(
                target = target.storageKey,
                day = day.toEpochDay(),
                extraMinutes = (existing?.extraMinutes ?: 0) + minutes,
                unlimited = existing?.unlimited ?: false,
            ),
        )
        ruleDao.pruneExtensions(day.toEpochDay() - 7)
    }

    suspend fun unlimitedToday(target: RuleTarget, day: LocalDate = LocalDate.now()) {
        val existing = ruleDao.getExtension(target.storageKey, day.toEpochDay())
        ruleDao.upsertExtension(
            LimitExtensionEntity(target.storageKey, day.toEpochDay(), existing?.extraMinutes ?: 0, unlimited = true),
        )
    }

    // ---- Categories (groups) ----

    suspend fun addCategory(name: String): Long =
        categoryDao.insert(CategoryEntity(name = name.trim(), sortOrder = categories.value.size))

    suspend fun renameCategory(id: Long, name: String) = categoryDao.rename(id, name.trim())

    suspend fun deleteCategory(id: Long) {
        metaDao.clearCategory(id)
        clearRule(RuleTarget.Group(id))
        categoryDao.delete(id)
    }

    private fun TimeWindowEntity.toWindowOrNull(): TimeWindow? = runCatching {
        TimeWindow(daysMask, startMinute, endMinute)
    }.getOrNull()
}
