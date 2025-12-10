package ai.tenum.lua.parser.expression

import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ParserNavigationHelpers
import ai.tenum.lua.parser.TokenNavigator
import ai.tenum.lua.parser.ast.BinaryOp
import ai.tenum.lua.parser.ast.BooleanLiteral
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.FieldAccess
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionExpression
import ai.tenum.lua.parser.ast.IndexAccess
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.NilLiteral
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.ParenExpression
import ai.tenum.lua.parser.ast.Statement
import ai.tenum.lua.parser.ast.StringLiteral
import ai.tenum.lua.parser.ast.TableConstructor
import ai.tenum.lua.parser.ast.TableField
import ai.tenum.lua.parser.ast.UnaryOp
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.parser.parseParameterList

/**
 * Domain parser for Lua expressions.
 * Handles operator precedence and expression parsing.
 */
class ExpressionParser(
    override val nav: TokenNavigator,
    private val parseBlockCallback: (Array<out TokenType>) -> List<Statement>,
) : ParserNavigationHelpers {
    /**
     * Parse a complete expression (entry point).
     */
    fun parseExpression(): Expression = parseOrExpression()

    /**
     * Parse logical OR expression (lowest precedence).
     */
    private fun parseOrExpression(): Expression {
        var expr = parseAndExpression()

        while (match(TokenType.OR)) {
            val operator = previous()
            val right = parseAndExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse logical AND expression.
     */
    private fun parseAndExpression(): Expression {
        var expr = parseComparisonExpression()

        while (match(TokenType.AND)) {
            val operator = previous()
            val right = parseComparisonExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse comparison operators: <, <=, >, >=, ==, ~=.
     */
    private fun parseComparisonExpression(): Expression {
        var expr = parseBitwiseOrExpression()

        while (match(
                TokenType.LESS,
                TokenType.LESS_EQUAL,
                TokenType.GREATER,
                TokenType.GREATER_EQUAL,
                TokenType.EQUAL,
                TokenType.NOT_EQUAL,
            )
        ) {
            val operator = previous()
            val right = parseBitwiseOrExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse bitwise OR expression (|).
     */
    private fun parseBitwiseOrExpression(): Expression {
        var expr = parseBitwiseXorExpression()

        while (match(TokenType.BITWISE_OR)) {
            val operator = previous()
            val right = parseBitwiseXorExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse bitwise XOR expression (~).
     */
    private fun parseBitwiseXorExpression(): Expression {
        var expr = parseBitwiseAndExpression()

        while (match(TokenType.BITWISE_XOR)) {
            val operator = previous()
            val right = parseBitwiseAndExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse bitwise AND expression (&).
     */
    private fun parseBitwiseAndExpression(): Expression {
        var expr = parseBitShiftExpression()

        while (match(TokenType.BITWISE_AND)) {
            val operator = previous()
            val right = parseBitShiftExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse bit shift expressions (<<, >>).
     */
    private fun parseBitShiftExpression(): Expression {
        var expr = parseConcatExpression()

        while (match(TokenType.SHIFT_LEFT, TokenType.SHIFT_RIGHT)) {
            val operator = previous()
            val right = parseConcatExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse string concatenation (..).
     */
    private fun parseConcatExpression(): Expression {
        var expr = parseAdditiveExpression()

        while (match(TokenType.CONCAT)) {
            val operator = previous()
            val right = parseAdditiveExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse addition and subtraction (+, -).
     */
    private fun parseAdditiveExpression(): Expression {
        var expr = parseMultiplicativeExpression()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous()
            val right = parseMultiplicativeExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse multiplication, division, floor division, and modulo (*, /, //, %).
     */
    private fun parseMultiplicativeExpression(): Expression {
        var expr = parseUnaryExpression()

        while (match(TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.FLOOR_DIVIDE, TokenType.MODULO)) {
            val operator = previous()
            val right = parseUnaryExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse unary operators: not, -, #, ~.
     */
    private fun parseUnaryExpression(): Expression {
        if (match(TokenType.NOT, TokenType.MINUS, TokenType.HASH, TokenType.BITWISE_XOR)) {
            val operator = previous()
            val operand = parseUnaryExpression()
            return UnaryOp(operator, operand, line = operator.line)
        }

        return parsePowerExpression()
    }

    /**
     * Parse exponentiation (^) - right associative.
     */
    private fun parsePowerExpression(): Expression {
        var expr = parsePostfixExpression()

        if (match(TokenType.POWER)) {
            val operator = previous()
            // Right associative - unary operators allowed on right side
            val right = parseUnaryExpression()
            expr = BinaryOp(expr, operator, right, line = operator.line)
        }

        return expr
    }

    /**
     * Parse postfix expressions: field access, index access, function/method calls.
     * Handles Lua's syntactic sugar for function calls with tables and strings.
     */
    private fun parsePostfixExpression(): Expression {
        var expr = parsePrimaryExpression()

        while (true) {
            // Check for line break before ( or { to avoid ambiguity
            // In Lua: "local t = {}\n(f)()" is TWO statements, not "{}(f)()"
            // BUT: "(expr)\n(...)" is ONE statement (the call continues)
            val prevTokenLine = previous().line
            val currentLine = peek().line
            val hasLineBreak = currentLine > prevTokenLine

            // If there's a line break before {, stop parsing postfix
            if (hasLineBreak && check(TokenType.LEFT_BRACE)) {
                break
            }

            // If there's a line break before (, stop parsing UNLESS current expr can be chained
            // Chainable expressions: Variables, ParenExpression, results of calls, indexing, or field access
            // Non-chainable: literals (nil, true, false, numbers, strings, tables, functions)
            //
            // Examples that should STOP:
            //   nil\n(...)          -> two statements
            //   {}\n(...)           -> two statements
            //   "str"\n(...)        -> two statements
            //   function() end\n(...) -> two statements
            //   ...\n(...)          -> two statements (vararg not chainable)
            //
            // Examples that should CONTINUE:
            //   var\n(...)          -> one statement (variable CAN be called)
            //   (expr)\n(...)       -> one statement (paren protects)
            //   expr[i]\n(...)      -> one statement (call result of index)
            //   expr.f\n(...)       -> one statement (call field value)
            //   expr()\n(...)       -> one statement (call result of call)
            if (hasLineBreak && check(TokenType.LEFT_PAREN)) {
                val isChainable =
                    expr is Variable ||
                        expr is ParenExpression ||
                        expr is IndexAccess ||
                        expr is FieldAccess ||
                        expr is FunctionCall ||
                        expr is MethodCall
                if (!isChainable) {
                    break
                }
            }

            expr =
                when {
                    match(TokenType.LEFT_BRACKET) -> {
                        val bracketLine = previous().line
                        val index = parseExpression()
                        consume(TokenType.RIGHT_BRACKET, "']' expected")
                        IndexAccess(expr, index, line = bracketLine)
                    }
                    match(TokenType.DOT) -> {
                        val dotLine = previous().line
                        val field = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
                        FieldAccess(expr, field, line = dotLine)
                    }
                    match(TokenType.COLON) -> {
                        val colonLine = previous().line
                        val method = consume(TokenType.IDENTIFIER, "<name> expected").lexeme

                        // Method call can be with parentheses, a table constructor, or a single string literal
                        val args: List<Expression>
                        if (match(TokenType.LEFT_PAREN)) {
                            args =
                                if (check(TokenType.RIGHT_PAREN)) {
                                    emptyList()
                                } else {
                                    parseExpressionList()
                                }
                            consume(TokenType.RIGHT_PAREN, "')' expected")
                        } else if (match(TokenType.LEFT_BRACE)) {
                            // Table constructor sugar: obj:method{...} -> method(self, {...})
                            val braceLine = previous().line
                            val fields = parseTableFields()
                            consume(TokenType.RIGHT_BRACE, "'}' expected")
                            val tableArg = TableConstructor(fields, line = braceLine)
                            args = listOf(tableArg)
                        } else if (check(TokenType.STRING)) {
                            // String literal sugar: obj:method"..." -> method(self, "...")
                            advance()
                            val stringLine = previous().line
                            val stringArg = StringLiteral(previous().literal as String, line = stringLine)
                            args = listOf(stringArg)
                        } else {
                            error("function arguments expected")
                        }

                        MethodCall(expr, method, args, line = colonLine)
                    }
                    check(TokenType.LEFT_PAREN) -> {
                        match(TokenType.LEFT_PAREN)
                        val parenLine = previous().line
                        val args =
                            if (check(TokenType.RIGHT_PAREN)) {
                                emptyList()
                            } else {
                                parseExpressionList()
                            }
                        consume(TokenType.RIGHT_PAREN, "')' expected")
                        // Use paren line to match Lua 5.4.8 behavior
                        FunctionCall(expr, args, line = parenLine)
                    }
                    check(TokenType.LEFT_BRACE) -> {
                        match(TokenType.LEFT_BRACE)
                        // Function call with table constructor: f{...} is sugar for f({...})
                        // LEFT_BRACE already consumed by match()
                        val braceCallLine = previous().line
                        val fields = parseTableFields()
                        consume(TokenType.RIGHT_BRACE, "'}' expected")
                        val tableArg = TableConstructor(fields, line = braceCallLine)
                        FunctionCall(expr, listOf(tableArg), line = braceCallLine)
                    }
                    check(TokenType.STRING) -> {
                        // Function call with string literal: f"..." is sugar for f("...")
                        advance()
                        val stringLine = previous().line
                        val stringArg = StringLiteral(previous().literal as String, line = stringLine)
                        FunctionCall(expr, listOf(stringArg), line = stringLine)
                    }
                    else -> break
                }
        }

        return expr
    }

    /**
     * Parse primary expressions: literals, identifiers, parenthesized expressions, functions, tables.
     */
    private fun parsePrimaryExpression(): Expression =
        when {
            match(TokenType.NIL) -> NilLiteral(line = previous().line)
            match(TokenType.TRUE) -> BooleanLiteral(true, line = previous().line)
            match(TokenType.FALSE) -> BooleanLiteral(false, line = previous().line)
            match(TokenType.NUMBER) -> {
                val lit = previous().literal
                val value =
                    when (lit) {
                        is Long -> lit // Keep as Long to preserve precision
                        is Double -> lit
                        else -> (lit as? Double) ?: 0.0
                    }
                NumberLiteral(value, lit, line = previous().line)
            }
            match(TokenType.STRING) -> StringLiteral(previous().literal as String, line = previous().line)
            match(TokenType.VARARG) -> VarargExpression(line = previous().line)
            match(TokenType.IDENTIFIER) -> Variable(previous().lexeme, line = previous().line)
            match(TokenType.LEFT_PAREN) -> {
                val parenLine = previous().line
                val expr = parseExpression()
                consume(TokenType.RIGHT_PAREN, "')' expected")
                ParenExpression(expr, line = parenLine)
            }
            match(TokenType.LEFT_BRACE) -> parseTableConstructor()
            match(TokenType.FUNCTION) -> parseFunctionExpression()
            else -> {
                val token = peek()

                // Check if this is a malformed number (ERROR token that looks like a number)
                if (token.type == TokenType.ERROR && token.lexeme.isNotEmpty()) {
                    val firstChar = token.lexeme.first()
                    val isMalformedNumber =
                        firstChar.isDigit() ||
                            (firstChar == '.' && token.lexeme.length > 1 && token.lexeme[1].isDigit())

                    if (isMalformedNumber) {
                        error("malformed number near '${token.lexeme}' at line ${token.line}, column ${token.column}")
                    }
                }

                val tokenDesc = if (token.lexeme.isNotEmpty()) "'${token.lexeme}'" else token.type.name
                error("unexpected symbol near $tokenDesc at line ${token.line}, column ${token.column}")
            }
        }

    /**
     * Parse table constructor: { field1, field2, ... }.
     */
    private fun parseTableConstructor(): Expression {
        val tableLine = previous().line
        val fields = parseTableFields()
        consume(TokenType.RIGHT_BRACE, "'}' expected")
        return TableConstructor(fields, line = tableLine)
    }

    /**
     * Parse table fields: supports [expr]=expr, name=expr, and expr formats.
     * Assumes LEFT_BRACE has already been consumed.
     * Stops when RIGHT_BRACE is encountered or end of input.
     */
    private fun parseTableFields(): List<TableField> {
        val fields = mutableListOf<TableField>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            val field =
                when {
                    // [expr] = expr
                    match(TokenType.LEFT_BRACKET) -> {
                        val key = parseExpression()
                        consume(TokenType.RIGHT_BRACKET, "']' expected")
                        consume(TokenType.ASSIGN, "'=' expected")
                        val value = parseExpression()
                        TableField.RecordField(key, value)
                    }
                    // name = expr
                    check(TokenType.IDENTIFIER) && checkNext(TokenType.ASSIGN) -> {
                        val name = advance().lexeme
                        advance() // consume '='
                        val value = parseExpression()
                        TableField.NamedField(name, value)
                    }
                    // expr
                    else -> {
                        val value = parseExpression()
                        TableField.ListField(value)
                    }
                }

            fields.add(field)

            if (!match(TokenType.COMMA) && !match(TokenType.SEMICOLON)) {
                break
            }
        }

        return fields
    }

    /**
     * Parse function expression: function(params) body end.
     */
    private fun parseFunctionExpression(): Expression {
        val funcLine = previous().line

        consume(TokenType.LEFT_PAREN, "'(' expected")

        val (params, hasVararg) = parseParameterList(nav)

        consume(TokenType.RIGHT_PAREN, "')' expected")

        val body = parseBlockCallback(arrayOf(TokenType.END))

        consume(TokenType.END, "'end' expected")
        val endLine = nav.previous().line

        return FunctionExpression(params, hasVararg, body, line = funcLine, endLine = endLine)
    }

    /**
     * Parse comma-separated expression list.
     */
    fun parseExpressionList(): List<Expression> {
        val expressions = mutableListOf<Expression>()
        expressions.add(parseExpression())

        while (match(TokenType.COMMA)) {
            expressions.add(parseExpression())
        }

        return expressions
    }
}
