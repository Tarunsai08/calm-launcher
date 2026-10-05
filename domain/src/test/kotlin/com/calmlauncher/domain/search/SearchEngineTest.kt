package com.calmlauncher.domain.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchEngineTest {

    private fun doc(label: String, pkg: String = "pkg." + label.lowercase().replace(" ", ""), usage: Double = 0.0, hidden: Boolean = false, aliases: List<String> = emptyList()) =
        SearchDocument(id = label, label = label, aliases = aliases, packageName = pkg, hidden = hidden, usageScore = usage)

    private val apps = listOf(
        doc("YouTube"),
        doc("YouTube Music"),
        doc("Maps"),
        doc("Messages"),
        doc("Camera"),
        doc("Calendar"),
        doc("Calculator"),
        doc("Café Délice"),
        doc("Settings"),
        doc("WhatsApp"),
        doc("Spotify"),
    )
    private val index = SearchIndex(apps)

    private fun ids(q: String, options: SearchOptions = SearchOptions()) = SearchEngine.search(index, q, options).map { it.document.id }

    @Test
    fun `empty query returns nothing`() {
        assertTrue(ids("").isEmpty())
        assertTrue(ids("   ").isEmpty())
    }

    @Test
    fun `prefix matches come first`() {
        val result = ids("ca")
        assertEquals(listOf("Café Délice", "Calculator", "Calendar", "Camera"), result.take(4).sorted())
        assertTrue(result.indexOf("Calendar") < result.size)
    }

    @Test
    fun `exact match ranks above prefix`() {
        val result = ids("youtube")
        assertEquals("YouTube", result.first())
        assertTrue("YouTube Music" in result)
    }

    @Test
    fun `initials match multi word labels`() {
        assertEquals("YouTube Music", ids("ym").first())
    }

    @Test
    fun `camel case humps are word starts`() {
        assertTrue("YouTube" in ids("tube"))
        assertTrue("WhatsApp" in ids("app"))
    }

    @Test
    fun `case and diacritics are ignored`() {
        assertEquals("Café Délice", ids("CAFE DEL").first())
        assertEquals("Café Délice", ids("délice").first())
    }

    @Test
    fun `single typo still finds the app`() {
        assertTrue("Spotify" in ids("sptoify"), "transposition")
        assertTrue("Calendar" in ids("calender"), "substitution")
        assertTrue("Settings" in ids("setings"), "deletion")
    }

    @Test
    fun `fuzzy subsequence matches`() {
        assertTrue("WhatsApp" in ids("wtsp"))
    }

    @Test
    fun `single letters do not fuzzy match noise`() {
        val result = ids("z")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `usage breaks ties within the prefix group`() {
        val idx = SearchIndex(listOf(doc("Maps", usage = 0.1), doc("Messages", usage = 5.0)))
        assertEquals(listOf("Messages", "Maps"), SearchEngine.search(idx, "m").map { it.document.id })
    }

    @Test
    fun `prefix group always beats fuzzy group even with high usage`() {
        val idx = SearchIndex(listOf(doc("Telegram", usage = 100.0), doc("Tasks", usage = 0.0)))
        val result = SearchEngine.search(idx, "tas").map { it.document.id }
        assertEquals("Tasks", result.first())
    }

    @Test
    fun `hidden apps only appear on exact match when searchable`() {
        val idx = SearchIndex(listOf(doc("Secret Game", hidden = true), doc("Settings")))
        assertFalse("Secret Game" in SearchEngine.search(idx, "sec").map { it.document.id })
        assertTrue("Secret Game" in SearchEngine.search(idx, "secret game").map { it.document.id })
        val off = SearchOptions(hiddenSearchable = false)
        assertFalse("Secret Game" in SearchEngine.search(idx, "secret game", off).map { it.document.id })
    }

    @Test
    fun `aliases are searchable`() {
        val idx = SearchIndex(listOf(doc("Gmail", aliases = listOf("Work mail"))))
        assertEquals("Gmail", SearchEngine.search(idx, "work").first().document.id)
        assertTrue(SearchEngine.search(idx, "work").first().viaAlias)
    }

    @Test
    fun `package names are matched only when enabled`() {
        val idx = SearchIndex(listOf(doc("Chrome", pkg = "com.android.chrome")))
        assertTrue(SearchEngine.search(idx, "android").isEmpty())
        val result = SearchEngine.search(idx, "android", SearchOptions(matchPackageNames = true))
        assertEquals(MatchTier.PACKAGE, result.single().tier)
    }

    @Test
    fun `limit caps results`() {
        assertEquals(2, ids("c", SearchOptions(limit = 2)).size)
    }

    @Test
    fun `search over 500 apps is fast`() {
        val many = (0 until 600).map { doc("App number $it ${('a' + it % 26)}lpha") }
        val big = SearchIndex(many)
        val start = System.nanoTime()
        repeat(20) { SearchEngine.search(big, "app nu") }
        val perQueryMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        assertTrue(perQueryMs < 100.0, "took $perQueryMs ms")
    }

    @Test
    fun `damerau distance basics`() {
        assertEquals(0, SearchEngine.damerauLevenshtein("abc", "abc"))
        assertEquals(1, SearchEngine.damerauLevenshtein("abc", "acb"))
        assertEquals(1, SearchEngine.damerauLevenshtein("abc", "ab"))
        assertEquals(1, SearchEngine.damerauLevenshtein("abc", "abd"))
        assertEquals(3, SearchEngine.damerauLevenshtein("", "abc"))
    }

    @Test
    fun `subsequence score rejects non subsequences`() {
        assertEquals(0, SearchEngine.subsequenceScore("maps", "spam"))
        assertTrue(SearchEngine.subsequenceScore("whatsapp", "wap") > 0)
    }
}
