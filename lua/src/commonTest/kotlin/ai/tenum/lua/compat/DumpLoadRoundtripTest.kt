package ai.tenum.lua.compat

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.io.ChunkReader
import ai.tenum.lua.compiler.io.ChunkWriter
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DumpLoadRoundtripTest {
    private fun compileProto(source: String) =
        run {
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()
            val parser = Parser(tokens)
            val ast = parser.parse()
            val compiler = Compiler()
            compiler.compile(ast, "test")
        }

    @Test
    fun testChunkWriterReaderRoundtrip() {
        val proto = compileProto("return 42")

        val bytes = ChunkWriter.dump(proto)
        val readProto = ChunkReader.load(bytes)

        assertNotNull(readProto, "ChunkReader should return a Proto for valid chunk bytes")
        assertEquals(proto.instructions.size, readProto!!.instructions.size, "instruction count should match after roundtrip")
        assertEquals(proto.constants.size, readProto.constants.size, "constant count should match after roundtrip")
    }

    @Test
    fun testStringDumpConversionSymmetry() {
        val proto = compileProto("return 1 + 2")

        // Bytes produced by the writer
        val bytes = ChunkWriter.dump(proto)

        // Simulate string.dump conversion: bytes -> String (each byte -> char code 0..255)
        val dumpedString = bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")

        // Simulate BasicLib.loadImpl conversion back: String -> ByteArray via char.code.toByte()
        val reconBytes = dumpedString.map { it.code.toByte() }.toByteArray()

        assertEquals(bytes.size, reconBytes.size, "reconstructed byte array should have same length")
        assertTrue(bytes.contentEquals(reconBytes), "bytes should be identical after roundtrip via Lua string mapping")

        // Ensure the reader accepts the reconstructed bytes
        val proto2 = ChunkReader.load(reconBytes)
        assertNotNull(proto2, "ChunkReader should accept reconstructed bytes")
    }
}
