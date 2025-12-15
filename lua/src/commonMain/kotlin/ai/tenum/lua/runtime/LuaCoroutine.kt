package ai.tenum.lua.runtime

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.vm.CallFrame

/**
 * Represents a Lua coroutine.
 *
 * Coroutines in Lua are collaborative threads that can be suspended and resumed.
 * They maintain their own stack and execution state.
 */
sealed class LuaCoroutine : LuaValue<Any> {
    abstract val status: CoroutineStatus
    abstract val thread: CoroutineThread

    override fun type(): LuaType = LuaType.THREAD

    override var metatableStore: LuaValue<*>? = null

    /**
     * Coroutine created from a Lua function
     */
    class LuaFunctionCoroutine(
        val func: LuaFunction,
        var statusValue: CoroutineStatus = CoroutineStatus.SUSPENDED,
        val threadValue: CoroutineThread = CoroutineThread(),
    ) : LuaCoroutine() {
        override val value: Any = func
        override val status: CoroutineStatus get() = statusValue
        override val thread: CoroutineThread get() = threadValue
    }

    /**
     * Coroutine created from a Kotlin suspend function
     */
    class SuspendFunctionCoroutine(
        val suspendFunc: suspend (List<LuaValue<*>>) -> List<LuaValue<*>>,
        var statusValue: CoroutineStatus = CoroutineStatus.SUSPENDED,
        val threadValue: CoroutineThread = CoroutineThread(),
    ) : LuaCoroutine() {
        override val value: Any = suspendFunc as Any
        override val status: CoroutineStatus get() = statusValue
        override val thread: CoroutineThread get() = threadValue
    }
}

/**
 * Coroutine execution state
 */
enum class CoroutineStatus {
    SUSPENDED, // Created but not started, or yielded
    RUNNING, // Currently executing
    NORMAL, // Has resumed another coroutine
    DEAD, // Finished execution or errored
}

/**
 * Coroutine execution thread state
 */
class CoroutineThread : ai.tenum.lua.vm.debug.ThreadHookState {
    // Execution state
    var proto: Proto? = null
    var pc: Int = 0
    var registers: MutableList<LuaValue<*>>? = null
    var upvalues: List<Upvalue> = emptyList() // Save upvalues for correct closure context on resume
    var varargs: List<LuaValue<*>> = emptyList() // Save varargs for VARARG opcode
    var yieldTargetRegister: Int = 0 // Register where yield call results should go
    var yieldExpectedResults: Int = 0 // Number of results expected by CALL (c field)

    // Call stack boundary: index in global call stack where this coroutine's frames start
    // Used to filter out main thread frames when saving coroutine's call stack
    var callStackBase: Int = 0

    // Call stack saved when coroutine yields
    var savedCallStack: List<CallFrame> = emptyList()

    // Yielded values
    var yieldedValues: List<LuaValue<*>> = emptyList()

    // Return values when coroutine completes
    var returnValues: List<LuaValue<*>> = emptyList()

    // Continuation point for Kotlin suspend functions
    var continuation: Any? = null

    // Saved native call depth from the calling context (for proper yield boundary checks)
    var savedNativeCallDepth: Int = 0

    // Per-thread hook state (Lua 5.4 keeps hooks per coroutine)
    override var hookConfig: ai.tenum.lua.vm.debug.HookConfig = ai.tenum.lua.vm.debug.HookConfig.NONE
    override var hookInstructionCount: Int = 0
    override var hookInProgress: Boolean = false

    fun reset() {
        proto = null
        pc = 0
        registers = null
        upvalues = emptyList()
        varargs = emptyList()
        yieldTargetRegister = 0
        yieldExpectedResults = 0
        callStackBase = 0
        savedCallStack = emptyList()
        yieldedValues = emptyList()
        returnValues = emptyList()
        // Don't reset hook state - it persists across yields
        continuation = null
        savedNativeCallDepth = 0
    }
}

/**
 * Exception thrown by coroutine.yield() to suspend execution
 */
class LuaYieldException(
    val yieldedValues: List<LuaValue<*>>,
) : Exception("coroutine yielded") {
    // Track whether state has been saved to prevent duplicate saves
    var stateSaved: Boolean = false
}
