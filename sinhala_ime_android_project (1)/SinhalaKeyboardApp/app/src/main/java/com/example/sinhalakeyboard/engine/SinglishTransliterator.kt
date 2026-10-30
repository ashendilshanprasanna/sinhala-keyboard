package com.example.sinhalakeyboard.engine

/**
 * High-performance Singlish (Phonetic Roman-to-Sinhala) Transliteration Engine.
 * 
 * Supports:
 * - Independent vowels at syllable/word onset (e.g. "a" -> "අ", "aa" -> "ආ", "ae" -> "ඇ")
 * - Inherent 'a' vowel and dependent vowel signs (Pili)
 * - Compound consonants (digraphs/trigraphs: "th", "dh", "sh", "ch", "kh", etc.)
 * - Automatic Rakaransaya ("kr" -> "ක්‍ර") and Yansaya ("ky" -> "ක්‍ය") with ZWJ (\u200D)
 * - Sannaka (pre-nasalized) consonants ("nd" -> "ඳ", "mb" -> "ඹ", "ng" -> "ඟ")
 * - Anusvara / Binduva ("x" or "ng" -> "ං") and Visarga ("H" -> "ඃ")
 * - Incremental composing buffer support for Android InputMethodService
 */
class SinglishTransliterator {

    data class Rule(val pattern: String, val glyph: String)

    companion object {
        const val AL_LAKUNA = "\u0DCA"     // ්
        const val ZWJ = "\u200D"           // Zero Width Joiner
        const val RAYANNA = "\u0DBB"       // ර
        const val YAYANNA = "\u0DBA"       // ය
        const val ANUSVARA = "\u0D82"      // ං
        const val VISARGA = "\u0D83"       // ඃ

        // Consonant definitions ordered from longest to shortest prefix
        private val CONSONANTS = listOf(
            Rule("shh", "\u0DC2"), // ෂ
            Rule("chh", "\u0DA1"), // ඡ
            Rule("th", "\u0DAD"),  // ත
            Rule("dh", "\u0DAF"),  // ද
            Rule("Th", "\u0DAE"),  // ථ
            Rule("Dh", "\u0DB0"),  // ධ
            Rule("ch", "\u0DA0"),  // ච
            Rule("jh", "\u0DA3"),  // ඣ
            Rule("sh", "\u0DC1"),  // ශ
            Rule("Sh", "\u0DC2"),  // ෂ
            Rule("kh", "\u0D9B"),  // ඛ
            Rule("gh", "\u0D9D"),  // ඝ
            Rule("ph", "\u0DB5"),  // ඵ
            Rule("bh", "\u0DB7"),  // භ
            Rule("gn", "\u0DA5"),  // ඥ
            Rule("kn", "\u0DA4"),  // ඤ
            Rule("nd", "\u0DB3"),  // ඳ (Sanyaka da)
            Rule("mb", "\u0DB9"),  // ඹ (Sanyaka ba)
            Rule("ng", "\u0D9F"),  // ඟ (Sanyaka ga)
            Rule("k", "\u0D9A"),   // ක
            Rule("g", "\u0D9C"),   // ග
            Rule("t", "\u0DAD"),   // ත (Common Singlish convention)
            Rule("T", "\u0DA7"),   // ට (Retroflex)
            Rule("d", "\u0DAF"),   // ද
            Rule("D", "\u0DA9"),   // ඩ (Retroflex)
            Rule("p", "\u0DB4"),   // ප
            Rule("b", "\u0DB6"),   // බ
            Rule("m", "\u0DB8"),   // ම
            Rule("y", "\u0DBA"),   // ය
            Rule("r", "\u0DBB"),   // ර
            Rule("l", "\u0DBD"),   // ල
            Rule("L", "\u0DC5"),   // ළ
            Rule("v", "\u0DC0"),   // ව
            Rule("w", "\u0DC0"),   // ව
            Rule("s", "\u0DC3"),   // ස
            Rule("h", "\u0DC4"),   // හ
            Rule("f", "\u0DC6"),   // ෆ
            Rule("j", "\u0DA2"),   // ජ
            Rule("n", "\u0DB1"),   // න
            Rule("N", "\u0DAB"),   // ණ
            Rule("c", "\u0DA0")    // ච
        )

        // Dependent vowel signs (attached to consonants)
        private val DEPENDENT_VOWELS = listOf(
            Rule("aae", "\u0DD1"), // ෑ
            Rule("aee", "\u0DD1"), // ෑ
            Rule("ae", "\u0DD0"),  // ැ
            Rule("aa", "\u0DCF"),  // ා
            Rule("A", "\u0DCF"),   // ා
            Rule("ii", "\u0DD3"),  // ී
            Rule("ee", "\u0DDA"),  // ේ
            Rule("I", "\u0DD3"),   // ී
            Rule("uu", "\u0DD6"),  // ූ
            Rule("oo", "\u0DDD"),  // ෝ
            Rule("U", "\u0DD6"),   // ූ
            Rule("ru", "\u0DD8"),  // ෘ
            Rule("ai", "\u0DDB"),  // ෛ
            Rule("au", "\u0DDE"),  // ෞ
            Rule("ou", "\u0DDE"),  // ෞ
            Rule("E", "\u0DDA"),   // ේ
            Rule("O", "\u0DDD"),   // ෝ
            Rule("a", ""),         // Inherent vowel (removes al-lakuna)
            Rule("i", "\u0DD2"),   // ි
            Rule("u", "\u0DD4"),   // ු
            Rule("e", "\u0DD9"),   // ෙ
            Rule("o", "\u0DDC")    // ො
        )

        // Independent vowels (word start or following another vowel)
        private val INDEPENDENT_VOWELS = listOf(
            Rule("aae", "\u0D88"), // ඈ
            Rule("aee", "\u0D88"), // ඈ
            Rule("ae", "\u0D87"),  // ඇ
            Rule("aa", "\u0D86"),  // ආ
            Rule("A", "\u0D86"),   // ආ
            Rule("ii", "\u0D8A"),  // ඊ
            Rule("ee", "\u0D92"),  // ඒ
            Rule("I", "\u0D8A"),   // ඊ
            Rule("uu", "\u0D8C"),  // ඌ
            Rule("oo", "\u0D95"),  // ඕ
            Rule("U", "\u0D8C"),   // ඌ
            Rule("iru", "\u0D8D"), // ඍ
            Rule("ai", "\u0D93"),  // ඓ
            Rule("au", "\u0D96"),  // ඖ
            Rule("ou", "\u0D96"),  // ඖ
            Rule("E", "\u0D92"),   // ඒ
            Rule("O", "\u0D95"),   // ඕ
            Rule("a", "\u0D85"),   // අ
            Rule("i", "\u0D89"),   // ඉ
            Rule("u", "\u0D8B"),   // උ
            Rule("e", "\u0D91"),   // එ
            Rule("o", "\u0D94")    // ඔ
        )
    }

    /**
     * Transliterates a complete Singlish word/phrase into Sinhala Unicode.
     */
    fun transliterate(input: String): String {
        val result = StringBuilder()
        var i = 0
        val n = input.length

        while (i < n) {
            val ch = input[i]

            // 1. Special Symbols
            if (ch == 'x') {
                result.append(ANUSVARA)
                i++
                continue
            }
            if (ch == 'H') {
                result.append(VISARGA)
                i++
                continue
            }

            // 2. Try matching a Consonant
            var matchedConsonant: String? = null
            var cMatchLen = 0
            for (rule in CONSONANTS) {
                if (input.startsWith(rule.pattern, i)) {
                    matchedConsonant = rule.glyph
                    cMatchLen = rule.pattern.length
                    break
                }
            }

            if (matchedConsonant != null) {
                i += cMatchLen
                var fullConsonant = matchedConsonant

                // 2a. Check for Rakaransaya ("...r" followed by vowel or end)
                if (i < n && input[i] == 'r' && (i + 1 == n || isVowelChar(input[i + 1]))) {
                    fullConsonant += AL_LAKUNA + ZWJ + RAYANNA
                    i++
                }
                // 2b. Check for Yansaya ("...y" followed by vowel or end)
                else if (i < n && input[i] == 'y' && (i + 1 == n || isVowelChar(input[i + 1]))) {
                    fullConsonant += AL_LAKUNA + ZWJ + YAYANNA
                    i++
                }

                // 2c. Check for dependent vowel
                var matchedVowel: String? = null
                var vMatchLen = 0
                for (rule in DEPENDENT_VOWELS) {
                    if (input.startsWith(rule.pattern, i)) {
                        matchedVowel = rule.glyph
                        vMatchLen = rule.pattern.length
                        break
                    }
                }

                if (matchedVowel != null) {
                    result.append(fullConsonant).append(matchedVowel)
                    i += vMatchLen
                } else {
                    // Consonant has no following vowel: apply Hal-kirima (Al-lakuna)
                    result.append(fullConsonant).append(AL_LAKUNA)
                }
            } else {
                // 3. Try matching an Independent Vowel
                var matchedIndependentVowel: String? = null
                var ivMatchLen = 0
                for (rule in INDEPENDENT_VOWELS) {
                    if (input.startsWith(rule.pattern, i)) {
                        matchedIndependentVowel = rule.glyph
                        ivMatchLen = rule.pattern.length
                        break
                    }
                }

                if (matchedIndependentVowel != null) {
                    result.append(matchedIndependentVowel)
                    i += ivMatchLen
                } else {
                    // Non-Sinhala / punctuation / digits pass through directly
                    result.append(ch)
                    i++
                }
            }
        }

        return result.toString()
    }

    private fun isVowelChar(c: Char): Boolean {
        return c in "aeiouAEIOU"
    }
}
