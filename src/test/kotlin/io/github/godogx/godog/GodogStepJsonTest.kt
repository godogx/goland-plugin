package io.github.godogx.godog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GodogStepJsonTest {

    @Test
    fun `parses real WriteManifest output, unescaping backslashes in regexes`() {
        val json = """
            {
              "steps": [
                {
                  "expr": "^there are (\\d+) godogs${'$'}",
                  "file": "/a/godogs_test.go",
                  "line": 112
                }
              ],
              "test": {
                "name": "TestFeatures",
                "file": "/a/godogs_test.go",
                "line": 47
              }
            }
        """.trimIndent()

        val dump = GodogStepJson.parse(json)

        assertEquals(1, dump.steps.size)
        // Not "^there are (\\\\d+) godogs$" - a doubled backslash would stop matching digits.
        assertEquals("""^there are (\d+) godogs$""", dump.steps[0].expr)
        assertEquals(112, dump.steps[0].line)

        assertEquals("TestFeatures", dump.test?.name)
        assertEquals(47, dump.test?.line)
    }

    @Test
    fun `skips entries missing required fields instead of throwing`() {
        val dump = GodogStepJson.parse("""{"steps": [{"expr": "x"}]}""")

        assertEquals(0, dump.steps.size)
    }

    @Test
    fun `parses empty steps array`() {
        assertEquals(0, GodogStepJson.parse("""{"steps": []}""").steps.size)
    }

    @Test
    fun `test block is optional`() {
        assertNull(GodogStepJson.parse("""{"steps": []}""").test)
    }
}
