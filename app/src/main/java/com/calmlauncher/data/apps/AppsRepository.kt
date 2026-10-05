package com.calmlauncher.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.core.content.ContextCompat
import com.calmlauncher.data.db.AppCacheDao
import com.calmlauncher.data.db.AppCacheEntity
import com.calmlauncher.data.db.AppMetaDao
import com.calmlauncher.data.db.AppMetaEntity
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.domain.model.LauncherApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Single source of truth for launchable apps.
 *
 * Start-up: emits the cached list from Room first (no PackageManager calls on the critical
 * path), then rescans in the background and diffs. Afterwards only the affected package is
 * rescanned when [LauncherApps.Callback] or a package broadcast reports a change.
 */
class AppsRepository(
    private val context: Context,
    private val source: AppSource,
    private val cacheDao: AppCacheDao,
    private val metaDao: AppMetaDao,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val raw = MutableStateFlow<List<RawApp>?>(null)
    private val scanMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val collator: Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    /** All apps merged with metadata, sorted by label (locale-aware). Empty until first load. */
    val apps: StateFlow<List<LauncherApp>> = combine(raw.filterNotNull(), metaDao.observeAll()) { rawApps, metas ->
        merge(rawApps, metas)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** True once the cached or scanned list has been emitted. */
    val isLoaded: StateFlow<Boolean> = MutableStateFlow(false).also { flag ->
        scope.launch { raw.filterNotNull().first(); flag.value = true }
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshPackage(packageName, user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshPackage(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshPackage(packageName, user)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { refreshPackage(it, user) }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { refreshPackage(it, user) }
        }
    }

    /** Fallback path (some OEM builds drop LauncherApps callbacks) plus locale/profile changes. */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                -> refreshAll()
                else -> intent.data?.schemeSpecificPart?.let { pkg ->
                    refreshPackage(pkg, android.os.Process.myUserHandle())
                }
            }
        }
    }

    fun start() {
        scope.launch(io) {
            val cached = cacheDao.getAll()
            if (cached.isNotEmpty() && raw.value == null) {
                raw.value = cached.map { it.toRaw() }
            }
            rescanAll()
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(callback, mainHandler)

        val profileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        ContextCompat.registerReceiver(context, receiver, profileFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, packageFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun refreshAll() {
        scope.launch(io) { rescanAll() }
    }

    private suspend fun rescanAll() = scanMutex.withLock {
        val scanned = source.scanAll()
        raw.value = scanned
        cacheDao.replaceAll(scanned.map { it.toEntity() })
        cleanupOrphans(scanned)
    }

    private fun refreshPackage(packageName: String, user: UserHandle) {
        scope.launch(io) {
            scanMutex.withLock {
                val serial = source.serialOf(user)
                val fresh = source.scanPackage(packageName, user)
                val current = raw.value.orEmpty()
                val updated = current.filterNot { it.key.packageName == packageName && it.key.userSerial == serial } + fresh
                raw.value = updated
                cacheDao.deletePackage(packageName, serial)
                if (fresh.isNotEmpty()) cacheDao.upsertAll(fresh.map { it.toEntity() })
                cleanupOrphans(updated)
            }
        }
    }

    /** Removes metadata for apps that no longer exist (e.g. uninstalled favourites). */
    private suspend fun cleanupOrphans(current: List<RawApp>) {
        if (current.isEmpty()) return // Never wipe metadata on a failed/empty scan.
        val ids = current.map { it.key.id }.toSet()
        val packages = current.map { it.key.packageName }.toSet()
        val orphans = metaDao.getAll().filter { it.id !in ids && it.packageName !in packages }.map { it.id }
        if (orphans.isNotEmpty()) orphans.chunked(500).forEach { metaDao.deleteIds(it) }
        // An activity renamed by an update: move metadata to the new activity of the same package.
        val metas = metaDao.getAll()
        for (meta in metas.filter { it.id !in ids }) {
            val replacement = current.firstOrNull {
                it.key.packageName == meta.packageName && it.key.id != meta.id && metas.none { m -> m.id == it.key.id }
            } ?: continue
            metaDao.deleteIds(listOf(meta.id))
            metaDao.upsert(meta.copy(id = replacement.key.id))
        }
    }

    private fun merge(rawApps: List<RawApp>, metas: List<AppMetaEntity>): List<LauncherApp> {
        val metaById = metas.associateBy { it.id }
        return rawApps.map { r ->
            val m = metaById[r.key.id]
            LauncherApp(
                key = r.key,
                systemLabel = r.label,
                alias = m?.alias,
                isWorkProfile = r.isWork,
                isClone = r.isClone,
                hidden = m?.hidden ?: false,
                categoryId = m?.categoryId,
                favoriteOrder = m?.favoriteOrder,
                lastLaunchedAt = m?.lastLaunched ?: 0L,
                launchCount = m?.launchCount ?: 0,
            )
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    // ---- Metadata mutations ----

    private suspend fun editMeta(key: AppKey, edit: (AppMetaEntity) -> AppMetaEntity) = withContext(io) {
        val current = metaDao.get(key.id) ?: AppMetaEntity(id = key.id, packageName = key.packageName)
        metaDao.upsert(edit(current))
    }

    suspend fun setFavorite(key: AppKey, favorite: Boolean) {
        val nextOrder = (apps.value.mapNotNull { it.favoriteOrder }.maxOrNull() ?: -1) + 1
        editMeta(key) { it.copy(favoriteOrder = if (favorite) it.favoriteOrder ?: nextOrder else null) }
    }

    suspend fun setFavorites(ordered: List<AppKey>) = withContext(io) {
        val wanted = ordered.map { it.id }.toSet()
        val updates = ArrayList<AppMetaEntity>()
        for (meta in metaDao.getAll()) {
            if (meta.favoriteOrder != null && meta.id !in wanted) updates += meta.copy(favoriteOrder = null)
        }
        ordered.forEachIndexed { index, key ->
            val meta = metaDao.get(key.id) ?: AppMetaEntity(id = key.id, packageName = key.packageName)
            updates += meta.copy(favoriteOrder = index)
        }
        metaDao.upsertAll(updates)
    }

    suspend fun moveFavorite(key: AppKey, delta: Int) {
        val favorites = apps.value.filter { it.isFavorite }.sortedBy { it.favoriteOrder }.map { it.key }.toMutableList()
        val index = favorites.indexOf(key)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, favorites.lastIndex)
        if (target == index) return
        favorites.removeAt(index)
        favorites.add(target, key)
        setFavorites(favorites)
    }

    suspend fun setAlias(key: AppKey, alias: String?) =
        editMeta(key) { it.copy(alias = alias?.trim()?.takeIf { a -> a.isNotEmpty() }) }

    suspend fun setHidden(key: AppKey, hidden: Boolean) =
        editMeta(key) { it.copy(hidden = hidden, favoriteOrder = if (hidden) null else it.favoriteOrder) }

    suspend fun setCategory(key: AppKey, categoryId: Long?) = editMeta(key) { it.copy(categoryId = categoryId) }

    suspend fun recordLaunch(key: AppKey) =
        editMeta(key) { it.copy(lastLaunched = System.currentTimeMillis(), launchCount = it.launchCount + 1) }

    fun find(key: AppKey): LauncherApp? = apps.value.firstOrNull { it.key == key }

    fun findByPackage(packageName: String): LauncherApp? = apps.value.firstOrNull { it.key.packageName == packageName }

    fun labelFor(packageName: String): String = findByPackage(packageName)?.label ?: packageName

    private fun RawApp.toEntity() = AppCacheEntity(
        id = key.id,
        packageName = key.packageName,
        className = key.className,
        userSerial = key.userSerial,
        label = label,
        isWork = isWork,
        isClone = isClone,
    )

    private fun AppCacheEntity.toRaw() = RawApp(
        key = AppKey(packageName, className, userSerial),
        label = label,
        isWork = isWork,
        isClone = isClone,
    )
}
