package com.wordiq.app.learning

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

enum class AnswerOutcome { EXACT, TYPO, PARTIAL, WRONG, REVEALED }

data class GradeResult(
    val outcome: AnswerOutcome,
    val expected: String,
    val normalizedAnswer: String,
    val editDistance: Int,
    val lemmaRemembered: Boolean = false,
    val quantityIssue: FinnishQuantityIssue? = null,
)

class AnswerGrader {
    fun grade(
        answer: String,
        expectedAnswers: List<String>,
        expectedLemma: String? = null,
        revealed: Boolean = false,
    ): GradeResult {
        val expected = expectedAnswers.filter { it.isNotBlank() }.ifEmpty { listOf("") }
        if (revealed) return GradeResult(AnswerOutcome.REVEALED, expected.first(), normalize(answer), Int.MAX_VALUE)

        val normalizedAnswer = normalize(answer)
        val candidates = expected.map { it to normalize(it) }
        candidates.firstOrNull { it.second == normalizedAnswer }?.let {
            return GradeResult(AnswerOutcome.EXACT, it.first, normalizedAnswer, 0)
        }

        val closest = candidates.minByOrNull { levenshtein(normalizedAnswer, it.second) }!!
        val distance = levenshtein(normalizedAnswer, closest.second)
        val longest = max(normalizedAnswer.length, closest.second.length).coerceAtLeast(1)
        val typoLimit = when {
            longest <= 3 -> 0
            longest <= 7 -> 1
            else -> 2
        }
        if (distance <= typoLimit && distance.toDouble() / longest <= 0.25) {
            return GradeResult(
                AnswerOutcome.TYPO,
                closest.first,
                normalizedAnswer,
                distance,
                quantityIssue = FinnishQuantityDetector.detect(normalizedAnswer, closest.second),
            )
        }

        val lemmaRemembered = expectedLemma
            ?.let(::normalize)
            ?.let { it.isNotBlank() && it == normalizedAnswer }
            ?: false
        if (lemmaRemembered) {
            return GradeResult(AnswerOutcome.PARTIAL, closest.first, normalizedAnswer, distance, lemmaRemembered = true)
        }

        val commonPrefix = normalizedAnswer.zip(closest.second).takeWhile { it.first == it.second }.size
        val answerTokens = normalizedAnswer.split(' ').filter { it.length >= 2 }.toSet()
        val expectedTokens = closest.second.split(' ').filter { it.length >= 2 }.toSet()
        val meaningfulOverlap = answerTokens.intersect(expectedTokens).isNotEmpty()
        if (normalizedAnswer.length >= 2 && (commonPrefix >= max(2, closest.second.length / 2) || meaningfulOverlap)) {
            return GradeResult(AnswerOutcome.PARTIAL, closest.first, normalizedAnswer, distance)
        }
        return GradeResult(AnswerOutcome.WRONG, closest.first, normalizedAnswer, distance)
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('\u200C', ' ')
        .trim()
        .replace(Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}

fun splitAcceptedAnswers(value: String?): List<String> = value.orEmpty()
    .split(Regex("\\s*(?:[,;/،]|\\bor\\b)\\s*", RegexOption.IGNORE_CASE))
    .map(String::trim)
    .filter(String::isNotEmpty)

object HintGenerator {
    fun hint(expected: String, depth: Int): String {
        val chars = expected.toCharArray()
        if (chars.isEmpty()) return ""
        val shown = when (depth) {
            1 -> 1
            2 -> max(2, chars.size / 3)
            3 -> max(3, chars.size / 2)
            else -> chars.size
        }.coerceAtMost(chars.size)
        return chars.mapIndexed { index, char ->
            when {
                index < shown -> char
                char.isWhitespace() -> ' '
                else -> '_'
            }
        }.joinToString("")
    }
}
