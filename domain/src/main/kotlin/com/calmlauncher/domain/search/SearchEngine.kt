package com.calmlauncher.domain.search

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A searchable entry. Apps, settings shortcuts and tools all use this shape. */
data class SearchDocument(
    val id: String,
    val label: String,
    val aliases: List<String> = emptyList(),
    val packageName: String? = null,
    val hidden: Boolean = false,
    /** Higher means used more often / more recently. See [UsageScore]. */
    val usageScore: Double = 0.0,
)

enum class MatchTier(val group: Int) {
    EXACT(0),
    PREFIX(0),
    WORD_PREFIX(1),
    INITIALS(1),
    SUBSTRING(1),
    PACKAGE(2),
    FUZZY(2),
}

data class SearchHit(
    val document: SearchDocument,
    val tier: MatchTier,
    /** 0..100, only meaningful for [MatchTier.FUZZY]; higher is better. */
    val fuzzyScore: Int,
    val viaAlias: Boolean,
)

data class SearchOptions(
    val matchPackageNames: Boolean = false,
    /** Hidden documents are returned only on an exact label/alias match when true. */
    val hiddenSearchable: Boolean = true,
    val limit: Int = Int.MAX_VALUE,
)

/** Pre-computed, immutable search index. Build it off the main thread. */
class SearchIndex(documents: List<SearchDocument>) {
    internal class Entry(
        val doc: SearchDocument,
        val names: List<Name>,
        val normalizedPackage: String,
    )

    internal class Name(
        val normalized: String,
        val compact: String,
        val wordStarts: List<String>,
        val initials: String,
        val isAlias: Boolean,
    )

    internal val entries: List<Entry> = documents.map { doc ->
        val names = buildList {
            add(nameOf(doc.label, isAlias = false))
            doc.aliases.filter { it.isNotBlank() }.forEach { add(nameOf(it, isAlias = true)) }
        }
        Entry(doc, names, TextNormalizer.normalize(doc.packageName.orEmpty()))
    }

    val size: Int get() = entries.size

    private fun nameOf(text: String, isAlias: Boolean): Name {
        val normalized = TextNormalizer.normalize(text)
        return Name(
            normalized = normalized,
            compact = normalized.replace(" ", ""),
            wordStarts = TextNormalizer.wordStarts(text),
            initials = TextNormalizer.initials(text),
            isAlias = isAlias,
        )
    }
}

object SearchEngine {

    fun search(index: SearchIndex, rawQuery: String, options: SearchOptions = SearchOptions()): List<SearchHit> {
        val query = TextNormalizer.normalize(rawQuery)
        if (query.isEmpty()) return emptyList()
        val compactQuery = query.replace(" ", "")
        val hits = ArrayList<SearchHit>()
        for (entry in index.entries) {
            val hit = matchEntry(entry, query, compactQuery, options) ?: continue
            if (entry.doc.hidden && !(options.hiddenSearchable && hit.tier == MatchTier.EXACT)) continue
            hits += hit
        }
        hits.sortWith(rankComparator)
        return if (hits.size > options.limit) hits.subList(0, options.limit).toList() else hits
    }

    /** Ranking: exact/prefix group first, then usage (recency/frequency), then match quality. */
    val rankComparator: Comparator<SearchHit> = Comparator { a, b ->
        val groupCmp = a.tier.group.compareTo(b.tier.group)
        if (groupCmp != 0) return@Comparator groupCmp
        // An exact match always wins inside the first group.
        val exactCmp = (b.tier == MatchTier.EXACT).compareTo(a.tier == MatchTier.EXACT)
        if (exactCmp != 0) return@Comparator exactCmp
        val usageCmp = b.document.usageScore.compareTo(a.document.usageScore)
        if (usageCmp != 0) return@Comparator usageCmp
        val tierCmp = a.tier.compareTo(b.tier)
        if (tierCmp != 0) return@Comparator tierCmp
        val fuzzyCmp = b.fuzzyScore.compareTo(a.fuzzyScore)
        if (fuzzyCmp != 0) return@Comparator fuzzyCmp
        val aliasCmp = a.viaAlias.compareTo(b.viaAlias)
        if (aliasCmp != 0) return@Comparator aliasCmp
        a.document.label.compareTo(b.document.label, ignoreCase = true)
    }

    private fun matchEntry(
        entry: SearchIndex.Entry,
        query: String,
        compactQuery: String,
        options: SearchOptions,
    ): SearchHit? {
        var best: SearchHit? = null
        for (name in entry.names) {
            val candidate = matchName(entry.doc, name, query, compactQuery) ?: continue
            if (best == null || isBetter(candidate, best)) best = candidate
        }
        if (best == null && options.matchPackageNames && query.length >= 2 &&
            entry.normalizedPackage.contains(compactQuery)
        ) {
            best = SearchHit(entry.doc, MatchTier.PACKAGE, 0, viaAlias = false)
        }
        return best
    }

    private fun isBetter(a: SearchHit, b: SearchHit): Boolean =
        a.tier < b.tier || (a.tier == b.tier && a.fuzzyScore > b.fuzzyScore)

    private fun matchName(doc: SearchDocument, name: SearchIndex.Name, query: String, compactQuery: String): SearchHit? {
        val alias = name.isAlias
        fun hit(tier: MatchTier, score: Int = 0) = SearchHit(doc, tier, score, alias)
        return when {
            name.normalized == query || name.compact == compactQuery -> hit(MatchTier.EXACT, 100)
            name.normalized.startsWith(query) || name.compact.startsWith(compactQuery) -> hit(MatchTier.PREFIX, 90)
            name.wordStarts.any { it.startsWith(query) } -> hit(MatchTier.WORD_PREFIX, 80)
            compactQuery.length >= 2 && name.initials.startsWith(compactQuery) -> hit(MatchTier.INITIALS, 70)
            query.length >= 2 && name.normalized.contains(query) -> hit(MatchTier.SUBSTRING, 60)
            compactQuery.length >= 3 -> {
                val score = max(subsequenceScore(name.compact, compactQuery), typoScore(name, compactQuery))
                if (score > 0) hit(MatchTier.FUZZY, score) else null
            }
            else -> null
        }
    }

    /**
     * Scores [query] as an in-order subsequence of [text]. Returns 0 if not a subsequence.
     * Rewards consecutive runs and early matches; penalises long gaps.
     */
    fun subsequenceScore(text: String, query: String): Int {
        if (query.isEmpty() || text.isEmpty() || query.length > text.length) return 0
        var ti = 0
        var score = 0
        var run = 0
        var firstMatch = -1
        var gaps = 0
        for (qc in query) {
            var found = false
            while (ti < text.length) {
                if (text[ti] == qc) {
                    if (firstMatch < 0) firstMatch = ti
                    run++
                    score += 2 + min(run, 4)
                    ti++
                    found = true
                    break
                } else {
                    if (run > 0) gaps++
                    run = 0
                    ti++
                }
            }
            if (!found) return 0
        }
        val maxScore = query.length * 6
        val raw = score - gaps * 2 - min(firstMatch, 5)
        // Too scattered to be useful.
        if (gaps > query.length / 2 + 1) return 0
        return (raw * 50 / maxScore).coerceIn(1, 50)
    }

    /** Tolerates one typo (insert/delete/substitute/transpose) against a word prefix. */
    private fun typoScore(name: SearchIndex.Name, query: String): Int {
        if (query.length < 4) return 0
        val candidates = name.wordStarts + name.compact
        for (word in candidates) {
            for (len in (query.length - 1)..(query.length + 1)) {
                if (len <= 0 || len > word.length) continue
                if (damerauLevenshtein(word.substring(0, len), query) <= 1) return 40
            }
        }
        return 0
    }

    /** Optimal-string-alignment distance. Small inputs only. */
    fun damerauLevenshtein(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }
}

/** Combines launch frequency and recency into a single ranking score. */
object UsageScore {
    private const val HALF_LIFE_MS = 3.0 * 24 * 60 * 60 * 1000

    fun compute(launchCount: Int, lastLaunchedAt: Long, now: Long): Double {
        if (launchCount <= 0 && lastLaunchedAt <= 0L) return 0.0
        val frequency = ln(1.0 + launchCount.coerceAtLeast(0))
        val age = (now - lastLaunchedAt).coerceAtLeast(0L).toDouble()
        val recency = if (lastLaunchedAt > 0) 2.0 * exp(-ln(2.0) * age / HALF_LIFE_MS) else 0.0
        return frequency + recency
    }
}
