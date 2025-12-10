package ai.tenum.lua.stdlib

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date().getTime().toLong()
