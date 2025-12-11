/*
 * Copyright (c) 2024 Plantitude AI UG (haftungsbeschränkt)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Lua 5.4 Compatibility Test Suite - Basic Syntax
 *
 * Tests fundamental Lua syntax features:
 * - Statement separators (semicolons)
 * - Comments (single-line, multi-line, nested)
 * - Shebang support
 * - Whitespace handling
 *
 * Based on Lua 5.4.8 official test suite.
 */
class SyntaxCompatTest : LuaCompatTestBase() {
    // ========== Statement Separators ==========

    @Test
    fun testOptionalSemicolons() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1;
            local y = 2;
            x = x + y;
            return x;
        """,
            3.0,
        )
    }

    @Test
    fun testMixedSemicolonUsage() {
        //language=lua
        assertLuaNumber(
            """
            local a = 5; local b = 3
            local c = a - b;
            return c
        """,
            2.0,
        )
    }

    @Test
    fun testMultipleStatementsOneLine() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1; local y = 2; local z = 3
            return x + y + z
        """,
            6.0,
        )
    }

    @Test
    fun testLeadingSemicolons() {
        //language=lua
        assertLuaNumber(
            """
            ;;local x = 5
            return x
        """,
            5.0,
        )
    }

    @Test
    fun testMultipleLeadingSemicolons() {
        //language=lua
        assertLuaNumber(
            """
            ;;;return 42
        """,
            42.0,
        )
    }

    @Test
    fun testEmptyStatementBlock() {
        //language=lua
        assertLuaNil(
            """
            do ;;; end
            return nil
        """,
        )
    }

    @Test
    fun testReturnWithSemicolon() {
        //language=lua
        assertLuaNil(
            """
            local function f()
                if true then
                    return;
                end
            end
            return f()
        """,
        )
    }

    @Test
    fun testReturnWithSemicolonInLoop() {
        //language=lua
        assertLuaNumber(
            """
            local function f(i)
                while true do
                    if i > 0 then 
                        i = i - 1
                    else 
                        return i;
                    end;
                end;
            end
            return f(5)
        """,
            0.0,
        )
    }

    // ========== Comments ==========

    @Test
    fun testSingleLineComments() {
        //language=lua
        assertLuaNumber(
            """
            -- This is a comment
            local x = 5 -- inline comment
            -- Another comment
            return x
        """,
            5.0,
        )
    }

    @Test
    fun testMultiLineComments() {
        //language=lua
        assertLuaNumber(
            """
            --[[ This is a
                 multi-line comment
                 spanning several lines ]]
            local x = 10
            return x
        """,
            10.0,
        )
    }

    @Test
    fun testNestedComments() {
        //language=lua
        assertLuaNumber(
            """
            --[=[ This is a nested comment
                  --[[ inner comment ]]
                  still in outer comment
            ]=]
            local x = 7
            return x
        """,
            7.0,
        )
    }

    @Test
    fun testCommentsInCode() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1 -- initialize x
            --[[ 
                This is commented out:
                local y = 100
            ]]
            local y = 2 -- real y value
            return x + y -- should be 3
        """,
            3.0,
        )
    }

    @Test
    fun testCommentEdgeCases() {
        //language=lua
        assertLuaNumber(
            """
            local x = 5 --- triple dash is still a comment
            --[[]] -- empty multi-line comment
            --[=[]=] -- empty nested comment
            return x
        """,
            5.0,
        )
    }

    // ========== Shebang Support ==========
    // Note: Shebangs are handled at FILE LOADING level, not in load()
    // load("#...") should FAIL, but loadfile("file_with_shebang") should SUCCEED

    @Test
    fun testShebangLine() {
        // Shebang in load() should FAIL - this is correct Lua 5.4 behavior
        //language=lua
        val code =
            """
            local f, err = load("#!/usr/bin/env lua\nlocal x = 42")
            assert(not f, "load() should reject shebangs")
            assert(err ~= nil, "error message should be present")
            return true
            """.trimIndent()
        assertLuaBoolean(code, true)
    }

    @Test
    fun testShebangWithComment() {
        // Shebang in load() should FAIL - this is correct Lua 5.4 behavior
        //language=lua
        val code =
            """
            local f, err = load("#!/usr/bin/lua\n-- comment\nlocal x = 8")
            assert(not f, "load() should reject shebangs")
            assert(err ~= nil, "error message should be present")
            return true
            """.trimIndent()
        assertLuaBoolean(code, true)
    }

    // ========== Whitespace Handling ==========

    @Test
    fun testWhitespaceHandling() {
        //language=lua
        assertLuaNumber(
            """
            local   x   =   5
            local	y	=	3
            return x + y
        """,
            8.0,
        )
    }

    @Test
    fun testMixedWhitespace() {
        //language=lua
        assertLuaNumber(
            """
            local x=1
            local y = 2
            local z  =  3
            return x+y+z
        """,
            6.0,
        )
    }

    // ========== Function Calls Without Parentheses ==========

    @Test
    fun testFunctionCallWithStringLiteral() {
        //language=lua
        assertLuaString(
            """
            return type "hello"
        """,
            "string",
        )
    }

    @Test
    fun testFunctionCallWithTableConstructor() {
        //language=lua
        assertLuaNumber(
            """
            local function f(t) return t.x + t.y end
            return f{x=10, y=20}
        """,
            30.0,
        )
    }

    @Test
    fun testFunctionCallWithMultiLineString() {
        //language=lua
        assertLuaString(
            """
            return type [[hello
            world]]
        """,
            "string",
        )
    }

    @Test
    fun testChainedCallsWithoutParens() {
        //language=lua
        assertLuaString(
            """
            local function wrap(s) return {value=s} end
            local function getValue(t) return t.value end
            return getValue(wrap "test")
        """,
            "test",
        )
    }

    @Test
    fun testMethodCallWithoutParens() {
        //language=lua
        assertLuaString(
            """
            return string.upper "hello"
        """,
            "HELLO",
        )
    }

    @Test
    fun testPrintWithString() {
        //language=lua
        execute("""print "hello"""")
        // Just verify it doesn't crash
    }

    @Test
    fun testTableConstructorAsArgument() {
        //language=lua
        assertLuaNumber(
            """
            local function sum(t)
                local total = 0
                for i,v in ipairs(t) do
                    total = total + v
                end
                return total
            end
            return sum{1,2,3,4}
        """,
            10.0,
        )
    }
}
