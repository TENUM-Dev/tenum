package ai.tenum.lua.stdlib

/**
 * Lua Pattern Matcher
 *
 * Implements Lua 5.4 pattern matching semantics for string operations.
 *
 * Pattern Syntax:
 * - Character classes: %a %c %d %g %l %p %s %u %w %x (and uppercase negations)
 * - Magic characters: ( ) . % + - * ? [ ] ^ $
 * - Sets: [abc] [^abc] [a-z]
 * - Captures: (pattern)
 * - Repetition: + (1+) * (0+) - (0+ shortest) ? (0-1)
 * - Anchors: ^ (start) $ (end)
 * - Escape: % (escapes next character)
 */
class LuaPattern(
    private val pattern: String,
) {
    data class Capture(
        val start: Int,
        val end: Int,
        val text: String,
    )

    data class MatchResult(
        val start: Int,
        val end: Int,
        val captures: List<Capture>,
    )

    /**
     * Find first match of pattern in text starting from init position
     */
    fun find(
        text: String,
        init: Int = 0,
    ): MatchResult? {
        val startPos = init.coerceIn(0, text.length)

        // Check for anchor at start
        if (pattern.startsWith("^")) {
            val captures = mutableListOf<Capture>()
            val captureStarts = mutableListOf<Pair<Int, Int>>()
            val endPos = matchPattern(text, startPos, 1, captures, captureStarts)
            return if (endPos != null) {
                MatchResult(startPos, endPos, captures)
            } else {
                null
            }
        }

        // Try matching at each position
        for (i in startPos..text.length) {
            val captures = mutableListOf<Capture>()
            val captureStarts = mutableListOf<Pair<Int, Int>>()
            val endPos = matchPattern(text, i, 0, captures, captureStarts)
            if (endPos != null) {
                return MatchResult(i, endPos, captures)
            }
        }

        return null
    }

    /**
     * Find all matches (for gmatch iterator)
     */
    fun findAll(text: String): Sequence<MatchResult> =
        sequence {
            var pos = 0
            while (pos <= text.length) {
                val captures = mutableListOf<Capture>()
                val captureStarts = mutableListOf<Pair<Int, Int>>()
                val startPat = if (pattern.startsWith("^")) 1 else 0
                val endPos = matchPattern(text, pos, startPat, captures, captureStarts)
                if (endPos != null) {
                    val result = MatchResult(pos, endPos, captures)
                    yield(result)
                    pos = if (endPos > pos) endPos else pos + 1 // Avoid infinite loop on empty matches
                    if (startPat > 0) break // Anchored pattern only matches at start
                } else {
                    if (startPat > 0) break
                    pos++
                }
            }
        }

    /**
     * Match pattern starting at specific position in text
     */
    private fun matchPattern(
        text: String,
        textPos: Int,
        patternPos: Int,
        captures: MutableList<Capture>,
        captureStarts: MutableList<Pair<Int, Int>>,
    ): Int? {
        var tPos = textPos
        var pPos = patternPos

        while (pPos < pattern.length) {
            val pChar = pattern[pPos]

            // Handle capture end
            if (pChar == ')' && (pPos == 0 || pattern[pPos - 1] != '%')) {
                if (captureStarts.isNotEmpty()) {
                    val (captureTextStart, _) = captureStarts.last() // Don't remove yet
                    // Ensure valid substring bounds
                    val captureStart = minOf(captureTextStart, tPos)
                    val captureEnd = maxOf(captureTextStart, tPos)
                    captures.add(Capture(captureStart, captureEnd, text.substring(captureStart, captureEnd)))
                    captureStarts.removeLast() // Remove after adding capture
                    pPos++
                    continue
                }
            }

            // Handle capture start
            if (pChar == '(' && (pPos == 0 || pattern[pPos - 1] != '%')) {
                captureStarts.add(Pair(tPos, pPos))
                pPos++ // Skip '('
                continue
            }

            // Check for end anchor
            if (pChar == '$' && pPos == pattern.length - 1) {
                return if (tPos == text.length) tPos else null
            }

            // Check for repetition modifiers AFTER the current pattern item
            val itemSize = getItemSize(pPos)
            val nextPos = pPos + itemSize
            val nextChar = if (nextPos < pattern.length) pattern[nextPos] else null
            when (nextChar) {
                '*' -> {
                    // 0 or more (greedy)
                    val result = matchRepeat(text, tPos, pPos, nextPos + 1, 0, Int.MAX_VALUE, greedy = true, captures, captureStarts)
                    if (result != null) return result
                    return null
                }
                '+' -> {
                    // 1 or more (greedy)
                    val result = matchRepeat(text, tPos, pPos, nextPos + 1, 1, Int.MAX_VALUE, greedy = true, captures, captureStarts)
                    if (result != null) return result
                    return null
                }
                '-' -> {
                    // 0 or more (non-greedy/shortest)
                    val result = matchRepeat(text, tPos, pPos, nextPos + 1, 0, Int.MAX_VALUE, greedy = false, captures, captureStarts)
                    if (result != null) return result
                    return null
                }
                '?' -> {
                    // 0 or 1 (optional)
                    val result = matchRepeat(text, tPos, pPos, nextPos + 1, 0, 1, greedy = true, captures, captureStarts)
                    if (result != null) return result
                    return null
                }
            }

            // Regular character matching
            val (matched, consumed) = matchSingleItem(text, tPos, pPos)
            if (!matched) {
                return null
            }

            tPos += consumed
            pPos += getItemSize(pPos)
        }

        return tPos
    }

    /**
     * Get the size of a pattern item at given position
     */
    private fun getItemSize(pPos: Int): Int {
        if (pPos >= pattern.length) return 0
        val pChar = pattern[pPos]

        // Escape sequence
        if (pChar == '%' && pPos + 1 < pattern.length) {
            val escaped = pattern[pPos + 1]
            // %bxy is 4 chars
            if (escaped == 'b' && pPos + 3 < pattern.length) {
                return 4
            }
            // %f[set] includes the set
            if (escaped == 'f' && pPos + 2 < pattern.length && pattern[pPos + 2] == '[') {
                var idx = pPos + 3
                if (idx < pattern.length && pattern[idx] == '^') idx++
                while (idx < pattern.length && pattern[idx] != ']') idx++
                return idx - pPos + 1
            }
            return 2
        }

        // Character set
        if (pChar == '[') {
            var idx = pPos + 1
            if (idx < pattern.length && pattern[idx] == '^') idx++
            while (idx < pattern.length && pattern[idx] != ']') idx++
            return idx - pPos + 1
        }

        return 1
    }

    /**
     * Match repeated pattern (for *, +, -, ?)
     */
    private fun matchRepeat(
        text: String,
        textPos: Int,
        patPos: Int,
        nextPatPos: Int,
        min: Int,
        max: Int,
        greedy: Boolean,
        captures: MutableList<Capture>,
        captureStarts: MutableList<Pair<Int, Int>>,
    ): Int? {
        var count = 0
        var tPos = textPos

        // Match as many as possible
        val matches = mutableListOf<Int>()
        matches.add(textPos) // Include starting position (for 0 matches)

        while (count < max && tPos < text.length) {
            val (matched, consumed) = matchSingleItem(text, tPos, patPos)
            if (!matched) break
            tPos += consumed
            count++
            matches.add(tPos)
        }

        // Check minimum requirement
        if (count < min) return null

        // Helper to try matching at a position with capture backtracking
        fun tryMatchWithBacktrack(pos: Int): Int? {
            val capturesBefore = captures.size
            val captureStartsBefore = captureStarts.toList() // Save copy
            val result = matchPattern(text, pos, nextPatPos, captures, captureStarts)
            if (result != null) return result
            // Backtrack captures on failure
            while (captures.size > capturesBefore) {
                captures.removeLast()
            }
            // Restore captureStarts
            captureStarts.clear()
            captureStarts.addAll(captureStartsBefore)
            return null
        }

        if (greedy) {
            // Try longest match first
            for (i in matches.size - 1 downTo min) {
                val result = tryMatchWithBacktrack(matches[i])
                if (result != null) return result
            }
        } else {
            // Try shortest match first (non-greedy)
            for (i in min..matches.lastIndex) {
                val result = tryMatchWithBacktrack(matches[i])
                if (result != null) return result
            }
        }

        return null
    }

    /**
     * Match single pattern item at position
     * Returns (matched, characters consumed)
     */
    private fun matchSingleItem(
        text: String,
        textPos: Int,
        patPos: Int,
    ): Pair<Boolean, Int> {
        if (patPos >= pattern.length) return Pair(false, 0)

        val pChar = pattern[patPos]

        // Handle escape sequences
        if (pChar == '%' && patPos + 1 < pattern.length) {
            val escaped = pattern[patPos + 1]

            // Balanced pattern %bxy
            if (escaped == 'b' && patPos + 3 < pattern.length) {
                val openChar = pattern[patPos + 2]
                val closeChar = pattern[patPos + 3]
                val consumed = matchBalanced(text, textPos, openChar, closeChar)
                return Pair(consumed > 0, consumed)
            }

            // Frontier pattern %f[set]
            if (escaped == 'f' && patPos + 2 < pattern.length && pattern[patPos + 2] == '[') {
                val matched = matchFrontier(text, textPos, patPos + 2)
                return Pair(matched, 0) // Zero-width assertion
            }

            if (textPos >= text.length) return Pair(false, 0)
            val tChar = text[textPos]
            return Pair(matchCharacterClass(tChar, escaped), 1)
        }

        if (textPos >= text.length) return Pair(false, 0)
        val tChar = text[textPos]

        // Handle sets [abc] [^abc] [a-z]
        if (pChar == '[') {
            return Pair(matchSet(tChar, patPos), 1)
        }

        // Wildcard
        if (pChar == '.') return Pair(true, 1)

        // Literal character
        return Pair(tChar == pChar, 1)
    }

    /**
     * Match character class (%a, %d, etc.)
     */
    private fun matchCharacterClass(
        char: Char,
        classChar: Char,
    ): Boolean =
        when (classChar) {
            'a' -> char.isLetter()
            'c' -> char.code < 32 // Control characters
            'd' -> char.isDigit()
            'g' -> char.code > 32 // Printable except space
            'l' -> char.isLowerCase()
            'p' -> char.code in 33..47 || char.code in 58..64 || char.code in 91..96 || char.code in 123..126 // Punctuation
            's' -> char.isWhitespace()
            'u' -> char.isUpperCase()
            'w' -> char.isLetterOrDigit()
            'x' -> char.isDigit() || char.lowercaseChar() in 'a'..'f'
            'z' -> char == '\u0000' // Null byte
            // Uppercase = negation
            'A' -> !char.isLetter()
            'C' -> char.code >= 32
            'D' -> !char.isDigit()
            'G' -> char.code <= 32
            'L' -> !char.isLowerCase()
            'P' -> !(char.code in 33..47 || char.code in 58..64 || char.code in 91..96 || char.code in 123..126)
            'S' -> !char.isWhitespace()
            'U' -> !char.isUpperCase()
            'W' -> !char.isLetterOrDigit()
            'X' -> !(char.isDigit() || char.lowercaseChar() in 'a'..'f')
            'Z' -> char != '\u0000' // Not null byte
            else -> char == classChar // Escaped literal
        }

    /**
     * Match character set [abc] [^abc] [a-z]
     */
    private fun matchSet(
        char: Char,
        patPos: Int,
    ): Boolean {
        var pIdx = patPos + 1
        val negated =
            if (pIdx < pattern.length && pattern[pIdx] == '^') {
                pIdx++
                true
            } else {
                false
            }

        var matched = false
        while (pIdx < pattern.length && pattern[pIdx] != ']') {
            // Check for character class (e.g., %w, %d)
            if (pattern[pIdx] == '%' && pIdx + 1 < pattern.length && pattern[pIdx + 1] != ']') {
                val classChar = pattern[pIdx + 1]
                if (matchCharacterClass(char, classChar)) matched = true
                pIdx += 2
            } else if (pIdx + 2 < pattern.length && pattern[pIdx + 1] == '-' && pattern[pIdx + 2] != ']') {
                // Check for range
                val start = pattern[pIdx]
                val end = pattern[pIdx + 2]
                if (char in start..end) matched = true
                pIdx += 3
            } else {
                if (char == pattern[pIdx]) matched = true
                pIdx++
            }
        }

        return if (negated) !matched else matched
    }

    /**
     * Match balanced pattern %bxy - matches text between balanced x and y
     * Returns number of characters consumed (0 if no match)
     */
    private fun matchBalanced(
        text: String,
        textPos: Int,
        openChar: Char,
        closeChar: Char,
    ): Int {
        if (textPos >= text.length || text[textPos] != openChar) return 0

        var level = 0
        var pos = textPos

        while (pos < text.length) {
            val char = text[pos]
            if (char == openChar) {
                level++
            } else if (char == closeChar) {
                level--
                if (level == 0) {
                    return pos - textPos + 1 // Include both open and close chars
                }
            }
            pos++
        }

        return 0 // Unbalanced
    }

    /**
     * Match frontier pattern %f[set] - zero-width boundary assertion
     * Matches if char before does NOT match set and char after DOES match set
     * At string boundaries, treats missing char as null byte (\0)
     */
    private fun matchFrontier(
        text: String,
        textPos: Int,
        setPatPos: Int,
    ): Boolean {
        // Get char before current position (or null byte if at start)
        val charBefore = if (textPos > 0) text[textPos - 1] else '\u0000'

        // Get char at current position (or null byte if at end)
        val charAfter = if (textPos < text.length) text[textPos] else '\u0000'

        // Check if charBefore does NOT match set and charAfter DOES match set
        val beforeMatches = matchSet(charBefore, setPatPos)
        val afterMatches = matchSet(charAfter, setPatPos)

        return !beforeMatches && afterMatches
    }
}
