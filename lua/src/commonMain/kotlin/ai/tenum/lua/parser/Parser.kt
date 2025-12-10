package ai.tenum.lua.parser

import ai.tenum.lua.lexer.Token
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ast.Chunk
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.Statement
import ai.tenum.lua.parser.expression.ExpressionParser
import ai.tenum.lua.parser.statement.StatementParser

/**
 * Parser for Lua source code.
 * Converts tokens into an Abstract Syntax Tree (AST).
 * Delegates to domain-specific parsers for expressions and statements.
 */
class Parser(
    private val tokens: List<Token>,
) {
    private var current = 0
    private val nav = TokenNavigator(tokens, { current }, { current = it })

    private val statementParser: StatementParser
    private val expressionParser: ExpressionParser

    init {
        expressionParser =
            ExpressionParser(
                nav = nav,
                parseBlockCallback = { terminators -> statementParser.parseBlock(*terminators) },
            )

        statementParser =
            StatementParser(
                nav = nav,
                parseExpressionCallback = { expressionParser.parseExpression() },
                parseExpressionListCallback = { expressionParser.parseExpressionList() },
            )
    }

    /**
     * Parse the tokens into a Chunk (top-level AST node).
     */
    fun parse(): Chunk {
        val statements = mutableListOf<Statement>()

        while (!nav.isAtEnd()) {
            // Skip any leading semicolons (empty statements)
            while (nav.match(TokenType.SEMICOLON)) {
                // continue skipping
            }

            // If we've reached EOF after skipping semicolons, break
            if (nav.isAtEnd()) break

            val statement = statementParser.parseStatement()
            statements.add(statement)

            // If this was a return statement at top level, it must be the last statement
            if (statement is ReturnStatement) {
                // Allow optional semicolon after return
                nav.match(TokenType.SEMICOLON)

                // After return (and optional semicolon), only EOF is valid at top level
                if (!nav.isAtEnd()) {
                    // Check if we're seeing another semicolon or statement
                    if (nav.check(TokenType.SEMICOLON)) {
                        throw ParserException("<eof> expected near ';'", nav.peek())
                    } else {
                        throw ParserException("'return' must be the last statement in a chunk", nav.peek())
                    }
                }
                break
            }

            // Optional semicolon after non-return statement
            nav.match(TokenType.SEMICOLON)
        }

        return Chunk(statements, line = 1)
    }
}

class ParserException(
    message: String,
    val token: Token,
) : IllegalStateException(message)
