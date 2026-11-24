package com.antigravity.mobile.feature.security.mcp

import com.antigravity.mobile.contracts.McpEndpoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class McpConfigGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validToken = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"

    @Test
    fun `rejects all non-loopback hosts`() {
        val generator = McpConfigGenerator()
        val forbiddenHosts = listOf(
            "0.0.0.0",
            "192.168.1.50",
            "10.0.2.2",
            "172.16.0.1",
            "127.0.0.2",
            "::",
            "::1",
            "example.com",
            "http://127.0.0.1",
            "127.0.0.1:8080",
            "",
            "   ",
        )

        for (host in forbiddenHosts) {
            try {
                generator.generateConfigJson(
                    host = host,
                    port = 41234,
                    bearerToken = validToken,
                )
                fail("Expected IllegalArgumentException for non-loopback host '$host'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `rejects weak or malformed bearer tokens and invalid ports`() {
        val generator = McpConfigGenerator()

        val invalidTokens = listOf("", "short-token", "has space in token 12345", "has\nnewline12345678")
        for (token in invalidTokens) {
            try {
                generator.validateLoopbackEndpoint("127.0.0.1", 41234, token)
                fail("Expected IllegalArgumentException for invalid token '$token'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }

        for (port in listOf(0, -1, 65536)) {
            try {
                generator.validateLoopbackEndpoint("127.0.0.1", port, validToken)
                fail("Expected IllegalArgumentException for invalid port $port")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `generates valid Antigravity mcp_config json pointing exclusively to 127_0_0_1 with Bearer auth`() {
        val generator = McpConfigGenerator()
        val jsonText = generator.generateConfigJson(
            host = "localhost",
            port = 43210,
            bearerToken = validToken,
            path = "/mcp",
        )

        val root = Json.parseToJsonElement(jsonText).jsonObject
        val servers = root["mcpServers"]!!.jsonObject
        val androidSystem = servers["android-system"]!!.jsonObject

        assertEquals("http", androidSystem["type"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:43210/mcp", androidSystem["url"]!!.jsonPrimitive.content)

        val headers = androidSystem["headers"]!!.jsonObject
        assertEquals("Bearer $validToken", headers["Authorization"]!!.jsonPrimitive.content)

        val env = androidSystem["env"]!!.jsonObject
        assertEquals("127.0.0.1", env["ANTIGRAVITY_MCP_HOST"]!!.jsonPrimitive.content)
        assertEquals("43210", env["ANTIGRAVITY_MCP_PORT"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:43210/mcp", env["ANTIGRAVITY_MCP_URL"]!!.jsonPrimitive.content)
        assertEquals(validToken, env["ANTIGRAVITY_MCP_TOKEN"]!!.jsonPrimitive.content)
    }

    @Test
    fun `writeConfigAtomically creates file, preserves existing servers, and leaves no temp files`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val generator = McpConfigGenerator(ioDispatcher = dispatcher)
        val configDir = File(tempFolder.root, "profile-1/.gemini/config")
        configDir.mkdirs()

        // Pre-populate an existing user MCP server
        val existingFile = File(configDir, McpConfigGenerator.DEFAULT_CONFIG_FILE_NAME)
        existingFile.writeText(
            """
            {
              "mcpServers": {
                "context7": {
                  "command": "npx",
                  "args": ["-y", "@upstash/context7-mcp@latest"]
                }
              }
            }
            """.trimIndent(),
        )

        val endpoint = McpEndpoint(
            host = "127.0.0.1",
            port = 49152,
            bearerToken = validToken,
        )
        val result = generator.writeConfigAtomically(configDir, endpoint)
        assertTrue(result.isSuccess)

        val writtenFile = result.getOrThrow()
        assertEquals(existingFile.canonicalPath, writtenFile.canonicalPath)
        assertTrue(writtenFile.isFile)

        // Ensure no temporary files remain
        val tempLeftovers = configDir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue("No temporary files should remain after atomic write", tempLeftovers.isEmpty())

        val parsed = Json.parseToJsonElement(writtenFile.readText()).jsonObject
        val servers = parsed["mcpServers"]!!.jsonObject
        assertTrue("Existing context7 server should be preserved", "context7" in servers)
        assertTrue("android-system server should be written", "android-system" in servers)
        assertEquals(
            "http://127.0.0.1:49152/mcp",
            servers["android-system"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )

        // Overwrite with preserveExistingServers = false drops context7
        val cleanWrite = generator.writeConfigAtomically(
            configDir = configDir,
            endpoint = endpoint.copy(port = 49153),
            preserveExistingServers = false,
        )
        assertTrue(cleanWrite.isSuccess)
        val parsedClean = Json.parseToJsonElement(writtenFile.readText()).jsonObject
        val cleanServers = parsedClean["mcpServers"]!!.jsonObject
        assertFalse("context7" in cleanServers)
        assertEquals(
            "http://127.0.0.1:49153/mcp",
            cleanServers["android-system"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }
}
