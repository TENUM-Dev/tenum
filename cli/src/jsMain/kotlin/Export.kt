import ai.tenum.cli.commands.Lua
import ai.tenum.cli.commands.Luac
import com.github.ajalt.clikt.core.main
import okio.NodeJsFileSystem

@OptIn(ExperimentalJsExport::class)
@JsExport
fun execLua(args: Array<String>): Int {
    return try {
        Lua().main(args)
        0
    } catch (e: Exception) {
        e.printStackTrace()
        1
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun execLuac(args: Array<String>): Int {
    return try {
        Luac().main(args)
        0
    } catch (e: Exception) {
        e.printStackTrace()
        1
    }
}