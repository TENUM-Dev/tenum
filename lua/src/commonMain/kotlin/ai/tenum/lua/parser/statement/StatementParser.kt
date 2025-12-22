package ai.tenum.lua.parser.statement

import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.ParserException
import ai.tenum.lua.parser.ParserNavigationHelpers
import ai.tenum.lua.parser.TokenNavigator
import ai.tenum.lua.parser.ast.Assignment
import ai.tenum.lua.parser.ast.BooleanLiteral
import ai.tenum.lua.parser.ast.BreakStatement
import ai.tenum.lua.parser.ast.DoStatement
import ai.tenum.lua.parser.ast.ElseIfBlock
import ai.tenum.lua.parser.ast.Expression
import ai.tenum.lua.parser.ast.ExpressionStatement
import ai.tenum.lua.parser.ast.ForInStatement
import ai.tenum.lua.parser.ast.ForStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionDeclaration
import ai.tenum.lua.parser.ast.GotoStatement
import ai.tenum.lua.parser.ast.IfStatement
import ai.tenum.lua.parser.ast.LabelStatement
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalFunctionDeclaration
import ai.tenum.lua.parser.ast.LocalVariableInfo
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.NilLiteral
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.RepeatStatement
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.Statement
import ai.tenum.lua.parser.ast.StringLiteral
import ai.tenum.lua.parser.ast.WhileStatement
import ai.tenum.lua.parser.parseParameterList

/**
 * Domain parser for Lua statements.
 * Handles control flow, declarations, and assignments.
 */
class StatementParser(
    override val nav: TokenNavigator,
    private val parseExpressionCallback: () -> Expression,
    private val parseExpressionListCallback: () -> List<Expression>,
) : ParserNavigationHelpers {
    /**
     * Parse a single statement (entry point).
     */
    fun parseStatement(): Statement =
        when {
            match(TokenType.LOCAL) -> parseLocalStatement()
            match(TokenType.IF) -> parseIfStatement()
            match(TokenType.WHILE) -> parseWhileStatement()
            match(TokenType.REPEAT) -> parseRepeatStatement()
            match(TokenType.FOR) -> parseForStatement()
            match(TokenType.FUNCTION) -> parseFunctionDeclaration()
            match(TokenType.DO) -> parseDoStatement()
            match(TokenType.RETURN) -> parseReturnStatement()
            match(TokenType.BREAK) -> {
                BreakStatement(line = previous().line)
            }
            match(TokenType.GOTO) -> {
                val gotoLine = previous().line
                val label = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
                GotoStatement(label, line = gotoLine)
            }
            match(TokenType.DOUBLE_COLON) -> {
                val labelLine = previous().line
                val name = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
                consume(TokenType.DOUBLE_COLON, "'::' expected")
                LabelStatement(name, line = labelLine)
            }
            else -> parseAssignmentOrExpressionStatement()
        }

    /**
     * Parses a local statement: local variable declaration or local function declaration.
     */
    private fun parseLocalStatement(): Statement {
        val localLine = previous().line
        if (match(TokenType.FUNCTION)) {
            // Local function declaration - use function keyword line
            val functionLine = previous().line
            val name = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
            consume(TokenType.LEFT_PAREN, "'(' expected")

            val (params, hasVararg) = parseParameterList(nav)

            consume(TokenType.RIGHT_PAREN, "')' expected")

            val body = parseBlock(TokenType.END)

            consume(TokenType.END, "'end' expected")
            val endLine = previous().line

            return LocalFunctionDeclaration(name, params, hasVararg, body, line = functionLine, endLine = endLine)
        } else {
            // Local variable declaration with optional attributes
            val variables = mutableListOf<LocalVariableInfo>()

            // Parse first variable with optional attributes
            val firstName = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
            val (firstConst, firstClose) = parseAttributes()
            variables.add(LocalVariableInfo(firstName, firstConst, firstClose))

            // Parse additional variables
            while (match(TokenType.COMMA)) {
                val name = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
                val (isConst, isClose) = parseAttributes()
                variables.add(LocalVariableInfo(name, isConst, isClose))
            }

            val expressions =
                if (match(TokenType.ASSIGN)) {
                    parseExpressionListCallback()
                } else {
                    emptyList()
                }

            return LocalDeclaration(variables, expressions, line = localLine)
        }
    }

    /**
     * Parse variable attributes: <const>, <close>, or <const, close>.
     * Returns a pair of (isConst, isClose).
     */
    private fun parseAttributes(): Pair<Boolean, Boolean> {
        var isConst = false
        var isClose = false

        if (match(TokenType.LESS)) {
            // Parse first attribute
            val firstAttr = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
            when (firstAttr) {
                "const" -> isConst = true
                "close" -> isClose = true
                else -> error("unknown attribute '$firstAttr'")
            }

            // Parse additional attributes separated by comma
            while (match(TokenType.COMMA)) {
                val attrName = consume(TokenType.IDENTIFIER, "<name> expected").lexeme
                when (attrName) {
                    "const" -> isConst = true
                    "close" -> isClose = true
                    else -> error("unknown attribute '$attrName'")
                }
            }

            consume(TokenType.GREATER, "'>' expected")
        }

        return Pair(isConst, isClose)
    }

    /**
     * Parse if-then-elseif-else-end statement.
     */
    private fun parseIfStatement(): Statement {
        val ifLine = previous().line
        val condition = parseExpressionCallback()
        consume(TokenType.THEN, "'then' expected")
        val thenLine = previous().line

        val thenBlock = parseBlock(TokenType.ELSEIF, TokenType.ELSE, TokenType.END)

        val elseIfBlocks = mutableListOf<ElseIfBlock>()
        while (check(TokenType.ELSEIF)) {
            advance() // consume 'elseif'
            val elseIfCondition = parseExpressionCallback()
            consume(TokenType.THEN, "'then' expected")
            val elseIfThenLine = previous().line
            val elseIfBlock = parseBlock(TokenType.ELSEIF, TokenType.ELSE, TokenType.END)
            elseIfBlocks.add(ElseIfBlock(elseIfCondition, elseIfBlock, elseIfThenLine))
        }

        val elseBlock =
            if (match(TokenType.ELSE)) {
                parseBlock(TokenType.END)
            } else {
                null
            }

        consume(TokenType.END, "'end' expected")
        val endLine = previous().line

        return IfStatement(condition, thenBlock, elseIfBlocks, elseBlock, line = ifLine, thenLine = thenLine, endLine = endLine)
    }

    /**
     * Parse while-do-end loop.
     */
    private fun parseWhileStatement(): Statement {
        val whileLine = previous().line
        val condition = parseExpressionCallback()
        consume(TokenType.DO, "'do' expected")

        val block = parseBlock(TokenType.END)

        consume(TokenType.END, "'end' expected")

        return WhileStatement(condition, block, line = whileLine)
    }

    /**
     * Parse repeat-until loop.
     */
    private fun parseRepeatStatement(): Statement {
        val repeatLine = previous().line
        val block = parseBlock(TokenType.UNTIL)

        consume(TokenType.UNTIL, "'until' expected")

        val condition = parseExpressionCallback()

        return RepeatStatement(block, condition, line = repeatLine)
    }

    /**
     * Parse for loop (numeric or generic iterator).
     */
    private fun parseForStatement(): Statement {
        val forLine = previous().line
        val name = consume(TokenType.IDENTIFIER, "<name> expected").lexeme

        // Check for attributes: <const>, <close>, or <const, close>
        val (isConst, isClose) = parseAttributes()

        if (match(TokenType.ASSIGN)) {
            // Numeric for loop: for i = start, end, step do ... end
            val start = parseExpressionCallback()
            consume(TokenType.COMMA, "',' expected")

            val end = parseExpressionCallback()

            val step =
                if (match(TokenType.COMMA)) {
                    parseExpressionCallback()
                } else {
                    null
                }

            consume(TokenType.DO, "'do' expected")

            val block = parseBlock(TokenType.END)

            consume(TokenType.END, "'end' expected")

            return ForStatement(name, start, end, step, block, isConst, isClose, line = forLine)
        } else {
            // Generic for loop: for k, v in pairs(t) do ... end
            val variables = mutableListOf(name)

            while (match(TokenType.COMMA)) {
                variables.add(consume(TokenType.IDENTIFIER, "<name> expected").lexeme)
            }

            consume(TokenType.IN, "'=' or 'in' expected")

            val expressions = parseExpressionListCallback()

            consume(TokenType.DO, "'do' expected")

            val block = parseBlock(TokenType.END)

            consume(TokenType.END, "'end' expected")

            return ForInStatement(variables, expressions, block, line = forLine)
        }
    }

    /**
     * Parse function declaration (global, table field, or method).
     */
    private fun parseFunctionDeclaration(): Statement {
        // Parse function name which can be:
        // - simple: function name()
        // - dot syntax: function t.name() or function a.b.c()
        // - colon syntax: function t:method()

        val functionLine = previous().line
        val nameParts = mutableListOf<String>()
        var isMethod = false

        // Parse first identifier
        nameParts.add(consume(TokenType.IDENTIFIER, "<name> expected").lexeme)

        // Parse any dot or colon separators
        while (match(TokenType.DOT, TokenType.COLON)) {
            if (previous().type == TokenType.COLON) {
                isMethod = true
                nameParts.add(consume(TokenType.IDENTIFIER, "<name> expected").lexeme)
                break // Colon can only appear once at the end
            } else {
                // DOT
                nameParts.add(consume(TokenType.IDENTIFIER, "<name> expected").lexeme)
            }
        }

        consume(TokenType.LEFT_PAREN, "'(' expected")

        val (params, hasVararg) = parseParameterList(nav)

        // If method syntax (colon), add implicit 'self' parameter at the beginning
        val finalParams =
            if (isMethod) {
                listOf("self") + params
            } else {
                params
            }

        consume(TokenType.RIGHT_PAREN, "')' expected")

        val body = parseBlock(TokenType.END)

        consume(TokenType.END, "'end' expected")
        val endLine = previous().line

        // Extract table path and function name
        // For "a.b.c", tablePath = ["a", "b"], name = "c"
        // For "a", tablePath = [], name = "a"
        val tablePath =
            if (nameParts.size > 1) {
                nameParts.dropLast(1)
            } else {
                emptyList()
            }
        val name = nameParts.last()

        return FunctionDeclaration(name, finalParams, hasVararg, body, tablePath, isMethod, line = functionLine, endLine = endLine)
    }

    /**
     * Parse do-end block.
     */
    private fun parseDoStatement(): Statement {
        val doLine = previous().line
        val block = parseBlock(TokenType.END)
        consume(TokenType.END, "'end' expected")
        return DoStatement(block, line = doLine)
    }

    /**
     * Parse return statement with optional expression list.
     */
    private fun parseReturnStatement(): Statement {
        val returnLine = previous().line
        val isBlockTerminator =
            check(TokenType.END) ||
                check(TokenType.ELSEIF) ||
                check(TokenType.ELSE) ||
                check(TokenType.UNTIL) ||
                check(TokenType.SEMICOLON) ||
                check(TokenType.EOF)

        val expressions =
            if (isBlockTerminator) {
                emptyList()
            } else {
                parseExpressionListCallback()
            }

        return ReturnStatement(expressions, line = returnLine)
    }

    /**
     * Parse assignment or expression statement (function/method call).
     */
    private fun parseAssignmentOrExpressionStatement(): Statement {
        val firstExpr = parseExpressionCallback()

        // Check if we have more variables (comma-separated)
        if (check(TokenType.COMMA)) {
            val variables = mutableListOf(firstExpr)
            while (match(TokenType.COMMA)) {
                variables.add(parseExpressionCallback())
            }

            val assignLine = peek().line
            consume(TokenType.ASSIGN, "'=' expected")

            val expressions = parseExpressionListCallback()

            return Assignment(variables, expressions, line = assignLine)
        }

        // Check if this is a single assignment
        if (match(TokenType.ASSIGN)) {
            val assignLine = previous().line
            val expressions = parseExpressionListCallback()
            return Assignment(listOf(firstExpr), expressions, line = assignLine)
        }

        // Expression statement validation
        // In Lua, only direct function/method calls are valid expression statements
        // Parenthesized calls like (f()) are NOT valid
        if (firstExpr !is FunctionCall && firstExpr !is MethodCall) {
            // Report error at the first token of the expression, not at peek() (which would be EOF)
            // This matches Lua 5.4 behavior where "1.000" reports near '1.000' not near <eof>
            val errorToken =
                when (firstExpr) {
                    is NumberLiteral, is StringLiteral, is BooleanLiteral, is NilLiteral ->
                        // For literals, use previous() to get the actual literal token
                        previous()
                    else ->
                        // For other expressions, use peek() as fallback
                        peek()
                }
            throw ParserException("syntax error", errorToken)
        }

        return ExpressionStatement(firstExpr, line = firstExpr.line)
    }

    /**
     * Parse a block of statements until one of the terminators is reached.
     */
    fun parseBlock(vararg terminators: TokenType): List<Statement> {
        val statements = mutableListOf<Statement>()

        while (!isAtEnd() && !checkAny(*terminators)) {
            // Skip any leading semicolons (empty statements)
            while (match(TokenType.SEMICOLON)) {
                // continue skipping
            }

            // If we've hit a terminator after skipping semicolons, break
            if (checkAny(*terminators) || isAtEnd()) break

            val statement = parseStatement()
            statements.add(statement)

            // If this was a return statement, it must be the last statement in the block
            if (statement is ReturnStatement) {
                // Allow optional semicolon after return
                match(TokenType.SEMICOLON)

                // After return (and optional semicolon), only block terminators are valid
                if (!isAtEnd() && !checkAny(*terminators)) {
                    // Check if we're seeing another semicolon or statement
                    if (check(TokenType.SEMICOLON)) {
                        error("<eof> expected near ';'")
                    } else {
                        error("'return' must be the last statement in a block")
                    }
                }
                break
            }

            // Optional semicolon after non-return statement
            match(TokenType.SEMICOLON)
        }

        return statements
    }
}
