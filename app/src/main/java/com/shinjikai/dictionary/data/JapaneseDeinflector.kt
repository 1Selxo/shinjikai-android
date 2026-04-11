package com.shinjikai.dictionary.data

private const val MAX_DEINFLECTION_DEPTH = 3
private const val MAX_DEINFLECTION_CANDIDATES = 24

internal object JapaneseDeinflector {
    fun generateCandidates(term: String): List<String> {
        val normalized = term.trim()
        if (normalized.isBlank()) return emptyList()

        val results = linkedSetOf(normalized)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(normalized to 0)

        while (queue.isNotEmpty() && results.size < MAX_DEINFLECTION_CANDIDATES) {
            val (current, depth) = queue.removeFirst()
            if (depth >= MAX_DEINFLECTION_DEPTH) continue

            rules.asSequence()
                .flatMap { it(current).asSequence() }
                .filter { it.isNotBlank() && results.add(it) }
                .take(MAX_DEINFLECTION_CANDIDATES - results.size)
                .forEach { queue.add(it to depth + 1) }
        }

        return results.toList()
    }

    private val rules: List<(String) -> List<String>> = listOf(
        ::applyIrregularRules,
        ::applyTaRules,
        ::applyTaraRules,
        ::applyTeRules,
        ::applyNegativeRules,
        ::applyPoliteNegativeRules,
        ::applyPotentialAndPassiveRules,
        ::applyCausativeRules,
        ::applyConditionalRules,
        ::applyVolitionalRules,
        ::applyImperativeRules,
        ::applyPoliteRules,
        ::applyAdjectiveRules
    )

    private fun applyIrregularRules(word: String): List<String> {
        return buildList {
            listOf("こない", "きた", "きて", "きます", "きました", "きません", "きませんでした")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix -> add(word.removeSuffix(suffix) + "くる") }

            listOf("しない", "した", "して", "します", "しました", "しません", "しませんでした")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix -> add(word.removeSuffix(suffix) + "する") }
        }
    }

    private fun applyTaRules(word: String): List<String> {
        return when {
            word.endsWith("った") -> replaceSuffix(word, "った", listOf("う", "つ", "る"))
            word.endsWith("んだ") -> replaceSuffix(word, "んだ", listOf("む", "ぶ", "ぬ"))
            word.endsWith("いた") -> replaceSuffix(word, "いた", listOf("く"))
            word.endsWith("いだ") -> replaceSuffix(word, "いだ", listOf("ぐ"))
            word.endsWith("した") -> replaceSuffix(word, "した", listOf("す"))
            word.endsWith("た") -> replaceSuffix(word, "た", listOf("る"))
            else -> emptyList()
        }
    }

    private fun applyTeRules(word: String): List<String> {
        return when {
            word.endsWith("って") -> replaceSuffix(word, "って", listOf("う", "つ", "る"))
            word.endsWith("んで") -> replaceSuffix(word, "んで", listOf("む", "ぶ", "ぬ"))
            word.endsWith("いて") -> replaceSuffix(word, "いて", listOf("く"))
            word.endsWith("いで") -> replaceSuffix(word, "いで", listOf("ぐ"))
            word.endsWith("して") -> replaceSuffix(word, "して", listOf("す"))
            word.endsWith("て") -> replaceSuffix(word, "て", listOf("る"))
            else -> emptyList()
        }
    }

    private fun applyTaraRules(word: String): List<String> {
        return when {
            word.endsWith("ったら") -> replaceSuffix(word, "ったら", listOf("う", "つ", "る"))
            word.endsWith("んだら") -> replaceSuffix(word, "んだら", listOf("む", "ぶ", "ぬ"))
            word.endsWith("いたら") -> replaceSuffix(word, "いたら", listOf("く"))
            word.endsWith("いだら") -> replaceSuffix(word, "いだら", listOf("ぐ"))
            word.endsWith("したら") -> replaceSuffix(word, "したら", listOf("す"))
            word.endsWith("たら") -> replaceSuffix(word, "たら", listOf("る"))
            else -> emptyList()
        }
    }

    private fun applyNegativeRules(word: String): List<String> {
        return buildList {
            if (word.endsWith("なかった")) {
                val stem = word.removeSuffix("なかった")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    negativeStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
            if (word.endsWith("ない")) {
                val stem = word.removeSuffix("ない")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    negativeStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
            if (word.endsWith("なくて") || word.endsWith("なければ")) {
                val suffix = if (word.endsWith("なくて")) "なくて" else "なければ"
                val stem = word.removeSuffix(suffix)
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    negativeStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
            if (word.endsWith("たくない")) {
                val stem = word.removeSuffix("たくない")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    masuStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
            if (word.endsWith("たかった")) {
                val stem = word.removeSuffix("たかった")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    masuStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
            if (word.endsWith("たい")) {
                val stem = word.removeSuffix("たい")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    masuStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
        }
    }

    private fun applyPoliteNegativeRules(word: String): List<String> {
        return buildList {
            val suffixes = listOf("なくありませんでした", "なくありません", "なくて", "なく", "ず", "ぬ")
            suffixes.firstOrNull { word.endsWith(it) }?.let { suffix ->
                val stem = word.removeSuffix(suffix)
                add(stem + "い")
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    negativeStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
        }
    }

    private fun applyPotentialAndPassiveRules(word: String): List<String> {
        return buildList {
            listOf("られました", "られません", "られない", "られなかった", "られた", "られて", "られる")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix -> add(word.removeSuffix(suffix) + "る") }

            listOf("れました", "れません", "れない", "れなかった", "れた", "れて", "れる")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix ->
                    val stem = word.removeSuffix(suffix)
                    add(stem + "る")
                    stem.lastOrNull()?.let { last ->
                        eStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                    }
                }
        }
    }

    private fun applyCausativeRules(word: String): List<String> {
        return buildList {
            listOf("させました", "させない", "させなかった", "させた", "させて", "させる")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix -> add(word.removeSuffix(suffix) + "る") }

            listOf("せました", "せない", "せなかった", "せた", "せて", "せる")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix ->
                    val stem = word.removeSuffix(suffix)
                    add(stem + "る")
                    stem.lastOrNull()?.let { last ->
                        aStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                    }
                }
        }
    }

    private fun applyConditionalRules(word: String): List<String> {
        return buildList {
            if (word.endsWith("ければ")) add(word.removeSuffix("ければ") + "い")

            listOf("えば", "けば", "げば", "せば", "てば", "ねば", "べば", "めば", "れば")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix ->
                    val stem = word.removeSuffix(suffix)
                    stem.lastOrNull()?.let { last ->
                        eStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                    }
                }
        }
    }

    private fun applyVolitionalRules(word: String): List<String> {
        return buildList {
            if (word.endsWith("よう")) add(word.removeSuffix("よう") + "る")

            listOf("おう", "こう", "ごう", "そう", "とう", "のう", "ぼう", "もう", "ろう")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix ->
                    val stem = word.removeSuffix(suffix)
                    val kana = suffix.first()
                    oStemToDictionary[kana]?.let { add(stem + it) }
                }
        }
    }

    private fun applyImperativeRules(word: String): List<String> {
        return buildList {
            if (word.endsWith("ろ")) add(word.removeSuffix("ろ") + "る")

            listOf("え", "け", "げ", "せ", "て", "ね", "べ", "め", "れ")
                .firstOrNull { word.endsWith(it) }
                ?.let { suffix ->
                    val stem = word.removeSuffix(suffix)
                    val kana = suffix.first()
                    eStemToDictionary[kana]?.let { add(stem + it) }
                }
        }
    }

    private fun applyPoliteRules(word: String): List<String> {
        return buildList {
            val politeSuffixes = listOf("ませんでした", "ました", "ません", "ます", "ましょう")
            politeSuffixes.firstOrNull { word.endsWith(it) }?.let { suffix ->
                val stem = word.removeSuffix(suffix)
                add(stem + "る")
                stem.lastOrNull()?.let { last ->
                    masuStemToDictionary[last]?.let { add(stem.dropLast(1) + it) }
                }
            }
        }
    }

    private fun applyAdjectiveRules(word: String): List<String> {
        return buildList {
            if (word.endsWith("かった")) add(word.removeSuffix("かった") + "い")
            if (word.endsWith("くない")) add(word.removeSuffix("くない") + "い")
            if (word.endsWith("くなかった")) add(word.removeSuffix("くなかった") + "い")
            if (word.endsWith("くて")) add(word.removeSuffix("くて") + "い")
            if (word.endsWith("ければ")) add(word.removeSuffix("ければ") + "い")
        }
    }

    private fun replaceSuffix(word: String, suffix: String, replacements: List<String>): List<String> {
        if (!word.endsWith(suffix) || word.length <= suffix.length) return emptyList()
        val stem = word.removeSuffix(suffix)
        return replacements.map { stem + it }
    }

    private val negativeStemToDictionary = mapOf(
        'わ' to "う",
        'か' to "く",
        'が' to "ぐ",
        'さ' to "す",
        'た' to "つ",
        'な' to "ぬ",
        'ば' to "ぶ",
        'ま' to "む",
        'ら' to "る"
    )

    private val aStemToDictionary = negativeStemToDictionary

    private val eStemToDictionary = mapOf(
        'え' to "う",
        'け' to "く",
        'げ' to "ぐ",
        'せ' to "す",
        'て' to "つ",
        'ね' to "ぬ",
        'べ' to "ぶ",
        'め' to "む",
        'れ' to "る"
    )

    private val oStemToDictionary = mapOf(
        'お' to "う",
        'こ' to "く",
        'ご' to "ぐ",
        'そ' to "す",
        'と' to "つ",
        'の' to "ぬ",
        'ぼ' to "ぶ",
        'も' to "む",
        'ろ' to "る"
    )

    private val masuStemToDictionary = mapOf(
        'い' to "う",
        'き' to "く",
        'ぎ' to "ぐ",
        'し' to "す",
        'ち' to "つ",
        'に' to "ぬ",
        'び' to "ぶ",
        'み' to "む",
        'り' to "る"
    )
}
