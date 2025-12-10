package ai.tenum.lua.runtime.table

enum class WeakMode {
    NONE,
    WEAK_KEYS,
    WEAK_VALUES,
    WEAK_KEYS_AND_VALUES,
    ;

    fun toModeLuaString(): String? =
        when (this) {
            NONE -> null
            WEAK_KEYS -> "k"
            WEAK_VALUES -> "v"
            WEAK_KEYS_AND_VALUES -> "kv"
        }

    companion object {
        fun parse(mode: String?): WeakMode =
            when (mode) {
                "k" -> WEAK_KEYS
                "v" -> WEAK_VALUES
                "kv", "vk" -> WEAK_KEYS_AND_VALUES
                else -> NONE
            }
    }
}
