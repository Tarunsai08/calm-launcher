package com.calmlauncher.data.apps

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.calmlauncher.domain.model.AppKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Icons are optional (off by default) and loaded lazily at the size they are drawn.
 * The cache is small and is trimmed whenever the system asks for memory.
 */
class IconCache(
    private val source: AppSource,
    private val io: CoroutineDispatcher,
) : ComponentCallbacks2 {
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(key: AppKey, sizePx: Int): Bitmap? = cache.get(cacheKey(key, sizePx))

    suspend fun load(key: AppKey, sizePx: Int): Bitmap? {
        val ck = cacheKey(key, sizePx)
        cache.get(ck)?.let { return it }
        return withContext(io) {
            val info = source.resolve(key) ?: return@withContext null
            val drawable = runCatching { info.getBadgedIcon(0) }.getOrNull() ?: return@withContext null
            val size = sizePx.coerceIn(16, 256)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            cache.put(ck, bitmap)
            bitmap
        }
    }

    fun clear() = cache.evictAll()

    private fun cacheKey(key: AppKey, size: Int) = "${key.id}@$size"

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cache.evictAll()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = cache.evictAll()

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = cache.evictAll()

    companion object {
        private const val MAX_BYTES = 6 * 1024 * 1024
    }
}
