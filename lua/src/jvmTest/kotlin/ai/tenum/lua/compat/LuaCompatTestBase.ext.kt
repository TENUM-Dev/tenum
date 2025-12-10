package ai.tenum.lua.compat

import ai.tenum.lua.runtime.LuaValue
import okio.Path.Companion.toPath
import java.io.InputStream
import kotlin.RuntimeException

fun LuaCompatTestBase.loadTestResources() {
    val testFiles =
        listOf(
            "all.lua",
            "api.lua",
            "attrib.lua",
            "big.lua",
            "bitwise.lua",
            "bwcoercion.lua",
            "calls.lua",
            "closure.lua",
            "code.lua",
            "constructs.lua",
            "coroutine.lua",
            "cstack.lua",
            "db.lua",
            "errors.lua",
            "events.lua",
            "files.lua",
            "gc.lua",
            "gengc.lua",
            "goto.lua",
            "heavy.lua",
            "literals.lua",
            "locals.lua",
            "main.lua",
            "math.lua",
            "nextvar.lua",
            "pm.lua",
            "sort.lua",
            "strings.lua",
            "tpack.lua",
            "tracegc.lua",
            "utf8.lua",
            "vararg.lua",
            "verybig.lua",
        )

    for (file in testFiles) {
        val resourcePath = "lua-5.4.8-tests/$file"
        try {
            val stream = this::class.java.classLoader.getResourceAsStream(resourcePath)
            if (stream != null) {
                val content = readResourceToUtf8(stream)
                fileSystem.write(file.toPath()) {
                    writeUtf8(content)
                }
            }
        } catch (_: Exception) {
            // Ignore files that don't exist or can't be loaded
        }
    }
}

/**
 * Load and execute a Lua test file from resources
 */
fun LuaCompatTestBase.executeTestFile(
    filename: String,
    vararg ignoreLineRanges: IntRange,
): LuaValue<*> {
    val resourcePath = "lua-5.4.8-tests/" + filename
    val stream =
        this::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: throw RuntimeException("Test resource not found: $resourcePath")
    loadTestResources()
    //language=Lua
    vm.execute(
        """
        _soft = true
        _port = true
        _nomsg = true
        """.trimIndent(),
    )
    var content = readResourceToUtf8(stream)

    // Strip shebang if present (Lua 5.4 behavior for files)
    if (content.startsWith("#")) {
        val firstNewline = content.indexOf('\n')
        content =
            if (firstNewline != -1) {
                content.substring(firstNewline + 1)
            } else {
                "" // Entire file is just the shebang line
            }
    }

    // Remove ignored line ranges
    if (ignoreLineRanges.isNotEmpty()) {
        val lines = content.lines().toMutableList()
        for (range in ignoreLineRanges) {
            for (lineNum in range.reversed()) {
                if (lineNum in 1..lines.size) {
                    // Replace line with a comment to preserve line numbers
                    lines[lineNum - 1] = "-- line $lineNum ignored for test compatibility"
                }
            }
        }
        content = lines.joinToString("\n")
    }
    return execute(content, filename)
}

// Helper: read an InputStream that may be UTF-8 (with optional BOM) or ISO-8859-1 and return UTF-8 normalized string
private fun readResourceToUtf8(stream: InputStream): String {
    val bytes = stream.readBytes()

    // If BOM present (UTF-8 BOM 0xEF,0xBB,0xBF), decode as UTF-8 and drop BOM
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        var s = bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        // Normalize CRLF -> LF
        s = s.replace("\r\n", "\n")
        return s
    }

    // Try decoding as UTF-8 first; if it contains replacement chars, fall back to ISO-8859-1 (Latin-1)
    val decodedUtf8 = bytes.toString(Charsets.UTF_8)
    val content =
        if (decodedUtf8.contains('\uFFFD')) {
            // Likely not valid UTF-8, decode as ISO-8859-1
            bytes.toString(Charsets.ISO_8859_1)
        } else {
            decodedUtf8
        }

    // Remove leading BOM just in case (U+FEFF character)
    val withoutBOM = if (content.startsWith("\uFEFF")) content.substring(1) else content

    // Normalize CRLF -> LF
    return withoutBOM.replace("\r\n", "\n")
}
