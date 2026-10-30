package com.example.sinhalakeyboard.engine

/**
 * SLS 1134 Standard Wijesekara Layout Engine with Stateful Keystroke Reordering.
 * 
 * Manages:
 * 1. Physical/Touch key to Sinhala glyph mapping (Normal and Shift layers)
 * 2. Kombuva (ෙ) Reordering State Machine:
 *    - In traditional Sri Lankan typing, users type Kombuva (ෙ) BEFORE the consonant.
 *    - In Unicode, Kombuva must follow the consonant (e.g. ක + ෙ = කෙ).
 *    - Handles subsequent modifiers:
 *      * Kombuva + Consonant + Aela-pilla (ා) -> කො (U+0DDC)
 *      * Kombuva + Consonant + Al-lakuna (්)   -> කේ (U+0DDA)
 *      * Kombuva + Consonant + Gayanukitta (ෟ) -> කෞ (U+0DDE)
 * 3. Ligature generation:
 *    - Yansaya (්‍ය), Rakaransaya (්‍ර), and Bandi Akuru (touching consonants) via ZWJ (\u200D).
 */
class WijesekaraLayoutEngine {

    companion object {
        const val KOMBUVA = "\u0DD9"          // ෙ
        const val DIGA_KOMBUVA = "\u0DDA"     // ේ
        const val KOMBUVA_AELA = "\u0DDC"     // ො
        const val DIGA_KOMBUVA_AELA = "\u0DDD"// ෝ
        const val KOMBUVA_GAYANUKITTA = "\u0DDE" // ෞ
        const val AL_LAKUNA = "\u0DCA"        // ්
        const val AELA_PILLA = "\u0DCF"       // ා
        const val GAYANUKITTA = "\u0DDF"      // ෟ
        const val ZWJ = "\u200D"              // Zero-width joiner

        // SLS 1134 Normal Layer
        val NORMAL_MAP = mapOf(
            'q' to "\u0DD4", // ු
            'w' to "\u0D85", // අ
            'e' to "\u0DD0", // ැ
            'r' to "\u0DBB", // ර
            't' to "\u0D91", // එ
            'y' to "\u0DC4", // හ
            'u' to "\u0DB8", // ම
            'i' to "\u0DC3", // ස
            'o' to "\u0DAF", // ද
            'p' to "\u0DA0", // ච
            '[' to "\u0DA4", // ඤ
            ']' to ";",
            'a' to "\u0DCA", // ් (Al-lakuna)
            's' to "\u0DD2", // ි
            'd' to "\u0DCF", // ා (Aela-pilla)
            'f' to "\u0DD9", // ෙ (Kombuva)
            'g' to "\u0DA7", // ට
            'h' to "\u0DBA", // ය
            'j' to "\u0DC0", // ව
            'k' to "\u0DB1", // න
            'l' to "\u0D9A", // ක
            ';' to "\u0DAD", // ත
            '\'' to "\u0DB4",// ප
            'z' to "’",
            'x' to "\u0D82", // ං (Binduva)
            'c' to "\u0DA2", // ජ
            'v' to "\u0DA9", // ඩ
            'b' to "\u0D89", // ඉ
            'n' to "\u0DB6", // බ
            'm' to "\u0DB4", // ප
            ',' to "\u0DBD", // ල
            '.' to "\u0D9C", // ග
            '/' to "/"
        )

        // SLS 1134 Shift Layer
        val SHIFT_MAP = mapOf(
            'Q' to "\u0DD6", // ූ
            'W' to "\u0D8B", // උ
            'E' to "\u0DD1", // ෑ
            'R' to "\u0D8D", // ඍ
            'T' to "\u0D94", // ඔ
            'Y' to "\u0DC1", // ශ
            'U' to "\u0DB9", // ඹ
            'I' to "\u0DC2", // ෂ
            'O' to "\u0DB0", // ධ
            'P' to "\u0DA1", // ඡ
            '{' to "\u0DA5", // ඥ
            '}' to ":",
            'A' to "\u0DDF", // ෟ (Gayanukitta)
            'S' to "\u0DD3", // ී
            'D' to "\u0DF2", // ෲ
            'F' to "\u0DDB", // ෛ (Kombu deka)
            'G' to "\u0DA8", // ඨ
            'H' to "\u0DCA\u200D\u0DBA", // ්‍ය (Yansaya shortcut)
            'J' to "\u0DC5\u0DD4",       // ළු
            'K' to "\u0DAB", // ණ
            'L' to "\u0D9B", // ඛ
            ':' to "\u0DAE", // ථ
            '"' to "\u0DB5", // ඵ
            'Z' to "”",
            'X' to "\u0D83", // ඃ (Visargaya)
            'C' to "\u0DA3", // ඣ
            'V' to "\u0DAA", // ඪ
            'B' to "\u0D8A", // ඊ
            'N' to "\u0DB7", // භ
            'M' to "\u0DBD", // ල
            '<' to "\u0DC5", // ළ
            '>' to "\u0D9D", // ඝ
            '?' to "\u0D9E"  // ඞ
        )
    }

    sealed class EngineAction {
        data class Commit(val text: String) : EngineAction()
        data class ReplacePrevious(val charsToDelete: Int, val text: String) : EngineAction()
        object BufferPendingKombuva : EngineAction()
    }

    private var isKombuvaBuffered = false
    private var lastEmittedConsonant: String? = null

    /**
     * Resets internal buffer state (called on space, punctuation, enter, or focus change).
     */
    fun resetState() {
        isKombuvaBuffered = false
        lastEmittedConsonant = null
    }

    /**
     * Processes a single key press from the Wijesekara keyboard.
     */
    fun processKey(keyChar: Char, isShift: Boolean): EngineAction {
        val mappedGlyph = if (isShift) {
            SHIFT_MAP[keyChar] ?: keyChar.toString()
        } else {
            NORMAL_MAP[keyChar] ?: keyChar.toString()
        }

        // Case 1: User pressed Kombuva (ෙ)
        if (mappedGlyph == KOMBUVA) {
            isKombuvaBuffered = true
            return EngineAction.BufferPendingKombuva
        }

        // Case 2: Kombuva was buffered and user typed a consonant
        if (isKombuvaBuffered) {
            isKombuvaBuffered = false
            if (isSinhalaConsonant(mappedGlyph)) {
                lastEmittedConsonant = mappedGlyph
                // Output Consonant + Kombuva (e.g. ක + ෙ = කෙ)
                return EngineAction.Commit(mappedGlyph + KOMBUVA)
            } else {
                // Not a consonant: flush Kombuva and the pressed glyph
                lastEmittedConsonant = null
                return EngineAction.Commit(KOMBUVA + mappedGlyph)
            }
        }

        // Case 3: Vowel modifier modification on a previously emitted Kombuva syllable
        if (lastEmittedConsonant != null) {
            val consonant = lastEmittedConsonant!!
            when (mappedGlyph) {
                AELA_PILLA -> {
                    // කෙ + ා -> කො
                    lastEmittedConsonant = null
                    return EngineAction.ReplacePrevious(2, consonant + KOMBUVA_AELA)
                }
                AL_LAKUNA -> {
                    // කෙ + ් -> කේ
                    lastEmittedConsonant = null
                    return EngineAction.ReplacePrevious(2, consonant + DIGA_KOMBUVA)
                }
                GAYANUKITTA -> {
                    // කෙ + ෟ -> කෞ
                    lastEmittedConsonant = null
                    return EngineAction.ReplacePrevious(2, consonant + KOMBUVA_GAYANUKITTA)
                }
            }
        }

        // Normal character output
        if (isSinhalaConsonant(mappedGlyph)) {
            lastEmittedConsonant = null
        }
        return EngineAction.Commit(mappedGlyph)
    }

    /**
     * Generates a Rakaransaya ligature for the given consonant (e.g., ක -> ක්‍ර).
     */
    fun makeRakaransaya(consonant: String): String {
        return consonant + AL_LAKUNA + ZWJ + "\u0DBB"
    }

    /**
     * Generates a Yansaya ligature for the given consonant (e.g., ක -> ක්‍ය).
     */
    fun makeYansaya(consonant: String): String {
        return consonant + AL_LAKUNA + ZWJ + "\u0DBA"
    }

    private fun isSinhalaConsonant(str: String): Boolean {
        if (str.isEmpty()) return false
        val cp = str.codePointAt(0)
        return cp in 0x0D9A..0x0DC6
    }
}
