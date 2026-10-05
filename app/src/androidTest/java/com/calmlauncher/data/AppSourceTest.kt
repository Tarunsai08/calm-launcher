package com.calmlauncher.data

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmlauncher.data.apps.AppSource
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Package discovery against the real device: full scan, incremental scan, resolve. */
@RunWith(AndroidJUnit4::class)
class AppSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = AppSource(context)

    @Test
    fun fullScanFindsLaunchableApps() {
        val apps = source.scanAll()
        assertTrue("expected some launchable apps", apps.isNotEmpty())
        assertTrue("labels are trimmed", apps.all { it.label == it.label.trim() })
        // The launcher never lists itself in the main profile.
        assertTrue(apps.none { it.key.packageName == context.packageName })
    }

    @Test
    fun incrementalScanMatchesFullScanForOnePackage() {
        val all = source.scanAll()
        val first = all.first()
        val user = Process.myUserHandle()
        val single = source.scanPackage(first.key.packageName, user)
        assertTrue(single.all { it.key.packageName == first.key.packageName })
        assertTrue(source.resolve(first.key) != null)
    }

    @Test
    fun removedPackageScansEmpty() {
        assertTrue(source.scanPackage("com.example.does.not.exist", Process.myUserHandle()).isEmpty())
    }
}
