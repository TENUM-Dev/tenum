package ai.tenum.lua.parser

import ai.tenum.lua.lexer.TokenType

/**
 * Parse function parameter list with optional vararg (...).
 * Assumes LEFT_PAREN already consumed, parses until RIGHT_PAREN.
 *
 * @return Pair of (parameter names, hasVararg flag)
 */
fun parseParameterList(nav: TokenNavigator): Pair<List<String>, Boolean> {
    val params = mutableListOf<String>()
    var hasVararg = false

    if (!nav.check(TokenType.RIGHT_PAREN)) {
        if (nav.match(TokenType.VARARG)) {
            hasVararg = true
        } else {
            params.add(nav.consume(TokenType.IDENTIFIER, "<name> or '...' expected").lexeme)

            while (nav.match(TokenType.COMMA)) {
                if (nav.match(TokenType.VARARG)) {
                    hasVararg = true
                    break
                }
                params.add(nav.consume(TokenType.IDENTIFIER, "<name> or '...' expected").lexeme)
            }
        }
    }

    return Pair(params, hasVararg)
}
