package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * OS Library for Lua 5.4 - Multiplatform implementation
 *
 * Provides operating system facilities including:
 * - Time functions (os.clock, os.time, os.date, os.difftime)
 * - Environment variables (os.getenv)
 * - File operations (os.remove, os.rename, os.tmpname)
 * - System execution (os.execute, os.exit)
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class OSLib : LuaLibrary {
    override val name: String = "os"

    private var tmpnameCounter = 0
    private val startTime = Clock.System.now()

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // Time functions
        lib[LuaString("clock")] =
            LuaNativeFunction { _ ->
                listOf(clockImpl())
            }

        lib[LuaString("time")] =
            LuaNativeFunction { args ->
                listOf(timeImpl(args))
            }

        lib[LuaString("date")] =
            LuaNativeFunction { args ->
                listOf(dateImpl(args))
            }

        lib[LuaString("difftime")] =
            LuaNativeFunction { args ->
                listOf(difftimeImpl(args))
            }

        // Locale
        lib[LuaString("setlocale")] =
            LuaNativeFunction { args ->
                val locale = (args.getOrNull(0) as? LuaString)?.value ?: "C"
                listOf(LuaString(locale))
            }

        // Environment
        lib[LuaString("getenv")] =
            LuaNativeFunction { args ->
                listOf(getenvImpl(args))
            }

        // File operations
        lib[LuaString("remove")] =
            LuaNativeFunction { args ->
                removeImpl(args, context)
            }

        lib[LuaString("rename")] =
            LuaNativeFunction { args ->
                renameImpl(args, context)
            }

        lib[LuaString("tmpname")] =
            LuaNativeFunction { _ ->
                listOf(tmpnameImpl())
            }

        // System execution
        lib[LuaString("execute")] =
            LuaNativeFunction { args ->
                executeImpl(args)
            }

        lib[LuaString("exit")] =
            LuaNativeFunction { args ->
                exitImpl(args)
            }

        // Platform information
        lib[LuaString("platform")] =
            LuaNativeFunction { _ ->
                listOf(LuaString(getPlatform()))
            }

        lib[LuaString("os")] =
            LuaNativeFunction { _ ->
                listOf(LuaString(getOs()))
            }

        context.registerGlobal("os", lib)
    }

    // ============================================
    // Time Functions
    // ============================================

    private fun clockImpl(): LuaValue<*> {
        val now = Clock.System.now()
        val duration = now - startTime
        return LuaNumber.of(duration.inWholeMilliseconds / 1000.0)
    }

    private fun timeImpl(args: List<LuaValue<*>>): LuaValue<*> {
        val tableArg = args.getOrNull(0) as? LuaTable

        return if (tableArg != null) {
            // Parse table with year, month, day, hour, min, sec
            val year = (tableArg[LuaString("year")] as? LuaNumber)?.value?.toInt()
            val month = (tableArg[LuaString("month")] as? LuaNumber)?.value?.toInt()
            val day = (tableArg[LuaString("day")] as? LuaNumber)?.value?.toInt()
            val hour = (tableArg[LuaString("hour")] as? LuaNumber)?.value?.toInt() ?: 12
            val min = (tableArg[LuaString("min")] as? LuaNumber)?.value?.toInt() ?: 0
            val sec = (tableArg[LuaString("sec")] as? LuaNumber)?.value?.toInt() ?: 0

            if (year == null || month == null || day == null) {
                throw RuntimeException("year, month, and day are required")
            }

            try {
                val localDateTime = LocalDateTime(year, month, day, hour, min, sec)
                val instant = localDateTime.toInstant(TimeZone.currentSystemDefault())
                LuaNumber.of(instant.epochSeconds.toDouble())
            } catch (e: Exception) {
                throw RuntimeException("invalid date: ${e.message}")
            }
        } else {
            // Return current timestamp
            LuaNumber.of(
                Clock.System
                    .now()
                    .epochSeconds
                    .toDouble(),
            )
        }
    }

    private fun dateImpl(args: List<LuaValue<*>>): LuaValue<*> {
        val format = (args.getOrNull(0) as? LuaString)?.value ?: "%c"
        val timestamp = (args.getOrNull(1) as? LuaNumber)?.value?.toLong()

        val instant =
            if (timestamp != null) {
                Instant.fromEpochSeconds(timestamp)
            } else {
                Clock.System.now()
            }

        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        return when (format) {
            "*t" -> {
                // Return table with date/time components
                val table = LuaTable()
                table[LuaString("year")] = LuaNumber.of(localDateTime.year)
                table[LuaString("month")] = LuaNumber.of(localDateTime.month.number)
                table[LuaString("day")] = LuaNumber.of(localDateTime.day)
                table[LuaString("hour")] = LuaNumber.of(localDateTime.hour)
                table[LuaString("min")] = LuaNumber.of(localDateTime.minute)
                table[LuaString("sec")] = LuaNumber.of(localDateTime.second)
                table[LuaString("wday")] = LuaNumber.of(localDateTime.dayOfWeek.isoDayNumber % 7 + 1) // Lua: 1=Sunday
                table[LuaString("yday")] = LuaNumber.of(localDateTime.dayOfYear)
                table[LuaString("isdst")] = LuaBoolean.FALSE // TODO: DST detection
                table
            }
            else -> {
                // Format string
                LuaString(formatDate(format, localDateTime))
            }
        }
    }

    private fun formatDate(
        format: String,
        dt: LocalDateTime,
    ): String {
        var result = format

        // Year
        result = result.replace("%Y", dt.year.toString().padStart(4, '0'))
        result = result.replace("%y", (dt.year % 100).toString().padStart(2, '0'))

        // Month
        result =
            result.replace(
                "%m",
                dt.month.number
                    .toString()
                    .padStart(2, '0'),
            )
        result =
            result.replace(
                "%B",
                dt.month.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
            )
        result =
            result.replace(
                "%b",
                dt.month.name
                    .substring(0, 3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
            )

        // Day
        result = result.replace("%d", dt.day.toString().padStart(2, '0'))
        result = result.replace("%e", dt.day.toString().padStart(2, ' '))

        // Hour
        result = result.replace("%H", dt.hour.toString().padStart(2, '0'))
        result = result.replace("%I", ((dt.hour % 12).let { if (it == 0) 12 else it }).toString().padStart(2, '0'))
        result = result.replace("%p", if (dt.hour < 12) "AM" else "PM")

        // Minute/Second
        result = result.replace("%M", dt.minute.toString().padStart(2, '0'))
        result = result.replace("%S", dt.second.toString().padStart(2, '0'))

        // Week day
        val weekdayName =
            dt.dayOfWeek.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        result = result.replace("%A", weekdayName)
        result = result.replace("%a", weekdayName.take(3))
        result = result.replace("%w", (dt.dayOfWeek.isoDayNumber % 7).toString()) // 0=Sunday in Lua

        // Day of year
        result = result.replace("%j", dt.dayOfYear.toString().padStart(3, '0'))

        // Common formats
        result =
            result.replace(
                "%c",
                "$weekdayName ${dt.month.name.substring(0, 3)} ${dt.day} ${dt.hour}:${dt.minute}:${dt.second} ${dt.year}",
            )
        result =
            result.replace(
                "%x",
                "${
                    dt.month.number.toString().padStart(
                        2,
                        '0',
                    )}/${dt.day.toString().padStart(2, '0')}/${dt.year.toString().substring(2)}",
            )
        result =
            result.replace(
                "%X",
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}",
            )

        // Literal %
        result = result.replace("%%", "%")

        return result
    }

    private fun difftimeImpl(args: List<LuaValue<*>>): LuaValue<*> {
        val t2 =
            (args.getOrNull(0) as? LuaNumber)?.value?.toLong()
                ?: throw RuntimeException("difftime requires two numbers")
        val t1 =
            (args.getOrNull(1) as? LuaNumber)?.value?.toLong()
                ?: throw RuntimeException("difftime requires two numbers")

        return LuaNumber.of((t2 - t1).toDouble())
    }

    // ============================================
    // Environment Variables
    // ============================================

    private fun getenvImpl(args: List<LuaValue<*>>): LuaValue<*> {
        val varName =
            (args.getOrNull(0) as? LuaString)?.value
                ?: throw RuntimeException("getenv requires a string argument")

        return try {
            val value = getEnvironmentVariable(varName)
            if (value != null) LuaString(value) else LuaNil
        } catch (e: Exception) {
            LuaNil
        }
    }

    // ============================================
    // File Operations
    // ============================================

    private fun removeImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val filename =
            (args.getOrNull(0) as? LuaString)?.value
                ?: throw RuntimeException("remove requires a filename")

        return try {
            val path = filename.toPath()
            if (!context.fileSystem.exists(path)) {
                return listOf(LuaNil, LuaString("No such file or directory"))
            }
            context.fileSystem.delete(path)
            listOf(LuaBoolean.TRUE)
        } catch (e: Exception) {
            listOf(LuaNil, LuaString(e.message ?: "error removing file"))
        }
    }

    private fun renameImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val oldName =
            (args.getOrNull(0) as? LuaString)?.value
                ?: throw RuntimeException("rename requires old filename")
        val newName =
            (args.getOrNull(1) as? LuaString)?.value
                ?: throw RuntimeException("rename requires new filename")

        return try {
            val oldPath = oldName.toPath()
            val newPath = newName.toPath()

            if (!context.fileSystem.exists(oldPath)) {
                return listOf(LuaNil, LuaString("No such file or directory"))
            }

            context.fileSystem.atomicMove(oldPath, newPath)
            listOf(LuaBoolean.TRUE)
        } catch (e: Exception) {
            listOf(LuaNil, LuaString(e.message ?: "error renaming file"))
        }
    }

    private fun tmpnameImpl(): LuaValue<*> {
        tmpnameCounter++
        val timestamp = Clock.System.now().nanosecondsOfSecond
        val random = Random.nextInt(1000, 9999)
        return LuaString("lua_tmp_${timestamp}_${tmpnameCounter}_$random")
    }

    // ============================================
    // System Execution
    // ============================================

    private fun executeImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val command = (args.getOrNull(0) as? LuaString)?.value

        // Empty command - just check if shell is available
        if (command == null || command.isEmpty()) {
            return listOf(LuaBoolean.TRUE)
        }

        // Execute command - platform-specific
        return try {
            val exitCode = executeSystemCommand(command)
            listOf(LuaBoolean.of(exitCode == 0), LuaString("exit"), LuaNumber.of(exitCode))
        } catch (e: Exception) {
            listOf(LuaNil, LuaString("exit"), LuaNumber.of(1))
        }
    }

    private fun exitImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val code =
            when (val arg = args.getOrNull(0)) {
                is LuaNumber -> arg.value.toInt()
                is LuaBoolean -> if (arg.value) 0 else 1
                else -> 0
            }

        // In a real implementation, this would call exitProcess(code)
        // For testing purposes, we throw an exception instead
        throw RuntimeException("os.exit called with code $code")
    }

    // ============================================
    // Platform-Specific Helpers
    // ============================================

    private fun getEnvironmentVariable(name: String): String? =
        try {
            // Platform-specific - on JVM uses System.getenv
            // On other platforms, returns null
            getPlatformEnvironmentVariable(name)
        } catch (e: Exception) {
            null
        }

    private fun executeSystemCommand(command: String): Int =
        try {
            // Platform-specific - on JVM uses Runtime.exec
            // On other platforms, returns 1 (failure)
            executePlatformCommand(command)
        } catch (e: Exception) {
            1
        }
}

expect fun getPlatform(): String

expect fun getOs(): String

expect fun getPlatformEnvironmentVariable(name: String): String?

expect fun executePlatformCommand(command: String): Int
