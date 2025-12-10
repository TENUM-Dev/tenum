package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

class Bit32Lib : LuaLibrary {
    override val name: String = "bit32"

    private val mask32 = 0xFFFFFFFFL

    override fun register(context: LuaLibraryContext) {
        val registerGlobal = context.registerGlobal

        val lib = LuaTable()

        fun toLongFrom(value: LuaValue<*>): Long =
            when (value) {
                is LuaNumber -> value.value.toLong()
                is LuaString -> {
                    val s = value.value.trim()
                    try {
                        s.toDouble().toLong()
                    } catch (e: Exception) {
                        throw RuntimeException("bad argument to bit32 function (number expected)")
                    }
                }
                else -> throw RuntimeException("bad argument to bit32 function (number expected)")
            }

        fun toUInt32(value: LuaValue<*>): Long {
            val v = toLongFrom(value)
            return v and mask32
        }

        lib[LuaString("bnot")] =
            LuaNativeFunction { args ->
                val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
                buildList { add(LuaNumber.of((a.inv()) and mask32)) }
            }

        lib[LuaString("band")] =
            LuaNativeFunction { args ->
                if (args.isEmpty()) return@LuaNativeFunction buildList { add(LuaNumber.of(mask32)) }
                var res = toUInt32(args[0])
                if (args.size == 1) res = res and mask32
                for (i in 1 until args.size) {
                    res = res and toUInt32(args[i])
                }
                buildList { add(LuaNumber.of(res and mask32)) }
            }

        // Register bitwise operations using descriptors
        val bitwiseOps =
            listOf(
                "bor" to { a: Long, b: Long -> a or b } to 0L,
                "bxor" to { a: Long, b: Long -> a xor b } to 0L,
            )

        for ((nameAndOp, emptyResult) in bitwiseOps) {
            val (name, op) = nameAndOp
            lib[LuaString(name)] =
                LuaNativeFunction { args ->
                    if (args.isEmpty()) return@LuaNativeFunction buildList { add(LuaNumber.of(emptyResult)) }
                    var res = toUInt32(args[0])
                    for (i in 1 until args.size) res = op(res, toUInt32(args[i]))
                    buildList { add(LuaNumber.of(res and mask32)) }
                }
        }

        lib[LuaString("btest")] =
            LuaNativeFunction { args ->
                // In Lua's bit32, calling btest() with no arguments returns true
                if (args.isEmpty()) return@LuaNativeFunction buildList { add(LuaBoolean.TRUE) }
                var res = toUInt32(args[0])
                for (i in 1 until args.size) res = res and toUInt32(args[i])
                buildList { add(LuaBoolean.of(res != 0L)) }
            }

        // Register shift operations using descriptors
        val shiftOps =
            listOf(
                "lshift" to ({ v: Long, n: Int -> v shl n } to { v: Long, n: Int -> v shr n }),
                "rshift" to ({ v: Long, n: Int -> v shr n } to { v: Long, n: Int -> v shl n }),
            )

        for ((name, ops) in shiftOps) {
            val (shiftPrimary, shiftOpposite) = ops
            lib[LuaString(name)] =
                LuaNativeFunction { args ->
                    if (args.size < 2) throw RuntimeException("bad argument to '$name' (expected 2 arguments)")
                    val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
                    val b = toLongFrom(args.getOrNull(1) ?: LuaNumber.of(0))
                    val res = performShift(a, b, shiftPrimary, shiftOpposite)
                    buildList { add(LuaNumber.of(res)) }
                }
        }

        lib[LuaString("arshift")] =
            LuaNativeFunction { args ->
                if (args.size < 2) throw RuntimeException("bad argument to 'arshift' (expected 2 arguments)")
                val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
                val b = toLongFrom(args.getOrNull(1) ?: LuaNumber.of(0))
                val signed = if ((a and 0x80000000L) != 0L) (a or (mask32.inv())) else a
                val res =
                    when {
                        b <= -32 -> 0L
                        b < 0 -> ((signed shl (-b.toInt())) and mask32)
                        b >= 32 -> if (signed < 0) mask32 else 0L
                        else -> ((signed shr b.toInt()) and mask32)
                    }
                buildList { add(LuaNumber.of(res)) }
            }

        // Helper for rotation operations
        fun rotate(
            args: List<LuaValue<*>>,
            rotateOp: (Long, Long) -> Long,
        ): List<LuaValue<*>> {
            val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
            var b = toLongFrom(args.getOrNull(1) ?: LuaNumber.of(0)) ?: 0L
            b = ((b % 32) + 32) % 32
            val res = rotateOp(a, b) and mask32
            return buildList { add(LuaNumber.of(res)) }
        }

        lib[LuaString("lrotate")] =
            LuaNativeFunction { args ->
                rotate(args) { a, b -> (a shl b.toInt()) or (a shr (32 - b.toInt())) }
            }

        lib[LuaString("rrotate")] =
            LuaNativeFunction { args ->
                rotate(args) { a, b -> (a shr b.toInt()) or (a shl (32 - b.toInt())) }
            }

        fun checkField(
            f: Int,
            w: Int,
        ) {
            if (f < 0) throw RuntimeException("field cannot be negative")
            if (w <= 0) throw RuntimeException("width must be positive")
            if (f + w > 32) throw RuntimeException("trying to access non-existent bits")
        }

        lib[LuaString("extract")] =
            LuaNativeFunction { args ->
                val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
                val f = (toLongFrom(args.getOrNull(1) ?: LuaNumber.of(0)) ?: 0L).toInt()
                val w = ((toLongFrom(args.getOrNull(2) ?: LuaNumber.of(1)) ?: 1L).toInt())
                checkField(f, w)
                val mask = (if (w == 32) mask32 else ((1L shl w) - 1L))
                val res = (a shr f) and mask
                buildList { add(LuaNumber.of(res)) }
            }

        lib[LuaString("replace")] =
            LuaNativeFunction { args ->
                val a = toUInt32(args.getOrNull(0) ?: LuaNumber.of(0))
                val v = toUInt32(args.getOrNull(1) ?: LuaNumber.of(0))
                val f = (toLongFrom(args.getOrNull(2) ?: LuaNumber.of(0)) ?: 0L).toInt()
                val w = ((toLongFrom(args.getOrNull(3) ?: LuaNumber.of(1)) ?: 1L).toInt())
                checkField(f, w)
                val mask = (if (w == 32) mask32 else ((1L shl w) - 1L))
                val vMasked = v and mask
                val fieldMask = (mask shl f) and mask32
                val clearMask = fieldMask.inv() and mask32
                val res = ((a and clearMask) or ((vMasked and mask) shl f)) and mask32
                buildList { add(LuaNumber.of(res)) }
            }

        // register table globally
        registerGlobal(name, lib)

        // Also expose as a require-able module via package.preload[name] = function() return <lib> end
        try {
            val packageTbl = context.getGlobal("package") as? LuaTable
            val preloadTbl = packageTbl?.get(LuaString("preload")) as? LuaTable
            if (preloadTbl != null) {
                preloadTbl[LuaString(name)] =
                    LuaNativeFunction { _ ->
                        buildList { add(lib) }
                    }
            }
        } catch (_: Exception) {
            // If package table is not available in this registration context, ignore.
        }
    }

    /**
     * Perform a bit shift operation with bounds checking.
     * @param a the value to shift
     * @param b the shift amount (positive for the specified direction, negative for opposite)
     * @param shiftPrimary lambda for primary shift direction
     * @param shiftOpposite lambda for opposite shift direction
     */
    private fun performShift(
        a: Long,
        b: Long,
        shiftPrimary: (Long, Int) -> Long,
        shiftOpposite: (Long, Int) -> Long,
    ): Long =
        when {
            b <= -32 -> 0L
            b < 0 -> shiftOpposite(a, (-b).toInt()) and mask32
            b >= 32 -> 0L
            else -> shiftPrimary(a, b.toInt()) and mask32
        }
}
