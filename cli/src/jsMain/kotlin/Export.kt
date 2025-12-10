import ai.tenum.cli.commands.Lua
import ai.tenum.cli.commands.Luac
import ai.tenum.cli.createCli
import com.github.ajalt.clikt.core.main
import okio.NodeJsFileSystem

@OptIn(ExperimentalJsExport::class)
@JsExport
fun execLua(args: Array<String>): Int {
    return try {
        Lua(
            NodeJsFileSystem
        ).main(args)
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
        Luac(
            NodeJsFileSystem
        ).main(args)
        0
    } catch (e: Exception) {
        e.printStackTrace()
        1
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun execTenum(args: Array<String>): Int {
    return try {
        createCli(
            NodeJsFileSystem
        ).main(args)
        0
    } catch (e: Exception) {
        e.printStackTrace()
        1
    }
}