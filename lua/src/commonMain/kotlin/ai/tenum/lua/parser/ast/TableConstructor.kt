package ai.tenum.lua.parser.ast

/**
 * Table constructor
 */
data class TableConstructor(
    val fields: List<TableField>,
    override val line: Int,
) : Expression

sealed interface TableField {
    data class ListField(
        val value: Expression,
    ) : TableField

    data class RecordField(
        val key: Expression,
        val value: Expression,
    ) : TableField

    data class NamedField(
        val name: String,
        val value: Expression,
    ) : TableField
}
