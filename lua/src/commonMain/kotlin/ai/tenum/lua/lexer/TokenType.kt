package ai.tenum.lua.lexer

/**
 * Represents the different types of tokens in Lua
 */
enum class TokenType {
    // Literals
    NUMBER,
    STRING,
    TRUE,
    FALSE,
    NIL,

    // Identifiers
    IDENTIFIER,

    // Keywords
    AND,
    BREAK,
    DO,
    ELSE,
    ELSEIF,
    END,
    FOR,
    FUNCTION,
    GOTO,
    IF,
    IN,
    LOCAL,
    NOT,
    OR,
    REPEAT,
    RETURN,
    THEN,
    UNTIL,
    WHILE,

    // Operators
    PLUS, // +
    MINUS, // -
    MULTIPLY, // *
    DIVIDE, // /
    FLOOR_DIVIDE, // //
    MODULO, // %
    POWER, // ^
    HASH, // #
    EQUAL, // ==
    NOT_EQUAL, // ~=
    LESS_EQUAL, // <=
    GREATER_EQUAL, // >=
    LESS, // <
    GREATER, // >
    ASSIGN, // =
    BITWISE_AND, // &
    BITWISE_OR, // |
    BITWISE_XOR, // ~
    SHIFT_LEFT, // <<
    SHIFT_RIGHT, // >>

    // Delimiters
    LEFT_PAREN, // (
    RIGHT_PAREN, // )
    LEFT_BRACE, // {
    RIGHT_BRACE, // }
    LEFT_BRACKET, // [
    RIGHT_BRACKET, // ]
    SEMICOLON, // ;
    COLON, // :
    DOUBLE_COLON, // ::
    COMMA, // ,
    DOT, // .
    CONCAT, // ..
    VARARG, // ...

    // Special
    EOF,
    ERROR,
}
