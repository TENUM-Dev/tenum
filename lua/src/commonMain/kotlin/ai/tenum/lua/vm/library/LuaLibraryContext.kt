package ai.tenum.lua.vm.library

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.CoroutineThread
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaThread
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.LuaVmImpl
import okio.FileSystem

/**
 * Context provided to libraries during registration
 */
data class LuaLibraryContext(
    val registerGlobal: RegisterGlobalCallback,
    val getGlobal: GetGlobalCallback,
    val getMetamethod: GetMetamethodCallback,
    val callFunction: CallFunctionCallback,
    val fileSystem: FileSystem,
    val executeChunk: ExecuteChunkCallback, // (chunk, source) -> result
    val loadedModules: MutableMap<String, LuaValue<*>>,
    val packagePath: List<String>,
    val vm: LuaVmImpl? = null, // VM reference for debug library (Phase 6.4)
    val executeProto: (
        (Proto, List<LuaValue<*>>, List<Upvalue>) -> List<LuaValue<*>>
    )? = null, // For load() function
    val executeProtoWithResume: (
        (
            Proto,
            List<LuaValue<*>>,
            List<Upvalue>,
            LuaFunction?,
            CoroutineThread?,
            Boolean,
        ) -> List<LuaValue<*>>
    )? = null, // For coroutine resumption with saved state
    val getCurrentEnv: (() -> Upvalue?)? = null, // Get current _ENV upvalue for load() inheritance
    val getCurrentCoroutine: (() -> LuaCoroutine?)? = null, // Get currently executing coroutine
    val getMainThread: (() -> LuaThread)? = null, // Get main thread
    val setCurrentCoroutine: ((LuaCoroutine?) -> Unit)? = null, // Set currently executing coroutine
    val getCallStack: (() -> List<CallFrame>)? = null, // Get current call stack for error reporting
    val getNativeCallDepth: (() -> Int)? = null, // Get current depth of native (C boundary) function calls
    val saveNativeCallDepth: ((Int) -> Unit)? = null, // Set native call depth (for coroutine context isolation)
    val getCoroutineStateManager: (() -> ai.tenum.lua.vm.coroutine.CoroutineStateManager)? = null, // Get coroutine state manager
    val cleanupCallStackFrames: ((Int) -> Unit)? = null, // Clean up call stack frames to a specific size (for coroutine cleanup)
)
