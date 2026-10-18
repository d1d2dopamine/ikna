package dev.ikna.desktop

import dev.ikna.data.db.openIknaDatabase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Abrupt JVM termination, not power-loss or physical-device evidence. All fixtures are synthetic. */
class DeviceSyncProcessIntegrationTest {
    @Test(timeout = 180_000) fun `process kill before page commit rolls back and resumes exact union`() = exercise("precommit")
    @Test(timeout = 180_000) fun `process kill after page commit before ack resumes without duplicate replay`() = exercise("postcommit")

    private fun exercise(point: String) = runBlocking {
        val root = Files.createTempDirectory("ikna-sync-process-").toFile()
        val children = mutableListOf<Child>()
        // Scheduler day boundaries must use the same captured zone in the oracle and child JVMs.
        val fixtureZone = java.util.TimeZone.getDefault().id
        fun launch(side: String) = Child(root.resolve(side).apply { mkdirs() }, fixtureZone).also { children += it }
        try {
            var a = launch("a"); var b = launch("b")
            assertNotEquals(a.pid, b.pid)
            assertNotEquals(ProcessHandle.current().pid(), a.pid)
            assertNotEquals(ProcessHandle.current().pid(), b.pid)
            a.call("seed", "legacy" to JsonPrimitive(true)); b.call("seed")
            a.call("enable"); b.call("enable")
            val invitation = a.call("invite").jsonPrimitive.content
            b.call("pair", "invitation" to JsonPrimitive(invitation)) // Never log this value.
            awaitPhase("paired profile previews", 30_000) {
                val x = a.status(); val y = b.status()
                x.number("peers") == 1L && y.number("peers") == 1L && x.flag("preview") && y.flag("preview")
            }
            a.call("propose")
            val operation = a.status().getValue("operation").jsonPrimitive.content
            awaitPhase("same explicit proposal", 15_000) { b.status()["operation"]?.jsonPrimitive?.content == operation }
            a.call("approve", "operation" to JsonPrimitive(operation))
            b.call("approve", "operation" to JsonPrimitive(operation))
            awaitPhase("initial legacy consent", 30_000) { caught(a.status(), 2) && caught(b.status(), 2) }
            a.call("pause"); b.call("pause")
            val initialA = a.call("manifest").jsonObject
            val initialB = b.call("manifest").jsonObject
            assertEquals(initialA["journalDigest"], initialB["journalDigest"])
            assertEquals(2L, initialB.number("remoteSequence"))
            a.call("append", "side" to JsonPrimitive("a")); b.call("append", "side" to JsonPrimitive("b"))
            val before = b.call("manifest").jsonObject
            assertEquals(19L, before.number("rows"))
            b.call("arm", "point" to JsonPrimitive(point))
            b.call("resume"); a.call("resume")
            val barrier = b.awaitFault(45_000)
            assertEquals(point, barrier.getValue("point").jsonPrimitive.content)
            assertEquals(point == "postcommit", barrier.flag("committed"))
            val atBarrier = barrier.getValue("manifest").jsonObject
            assertTrue("Barrier must follow incoming page inserts", atBarrier.number("rows") > before.number("rows"))
            assertTrue("Barrier must follow incoming index inserts", atBarrier.number("remoteIndexCount") > before.number("remoteIndexCount"))
            if (point == "precommit") assertEquals(before["remoteSequence"], atBarrier["remoteSequence"])
            else assertTrue(atBarrier.number("remoteSequence") > before.number("remoteSequence"))
            b.kill() // No graceful controller.close or Room.close before either crash.
            a.kill()
            assertFalse(a.alive); assertFalse(b.alive)

            a = launch("a"); b = launch("b")
            // Constructor leaves transport stopped; inspect durability before any local head indexing.
            assertFalse(a.status().flag("running")); assertFalse(b.status().flag("running"))
            val reopenedA = a.call("manifest").jsonObject
            val reopenedB = b.call("manifest").jsonObject
            assertEquals(initialA["streamId"], reopenedA["streamId"])
            assertEquals(initialB["streamId"], reopenedB["streamId"])
            assertEquals(operation, reopenedB.getValue("operation").jsonPrimitive.content)
            if (point == "precommit") {
                assertFields(before, reopenedB, DURABLE_PAGE_FIELDS)
                assertTrue(reopenedB.flag("localIndexValid"))
                assertEquals(reopenedB.number("rows"), reopenedB.number("localIndexCount"))
            } else {
                assertFields(atBarrier, reopenedB, DURABLE_PAGE_FIELDS + listOf("sourceSequence", "sourcePrefix", "localIndexCount", "localIndexDigest", "localIndexValid"))
            }
            a.call("resume"); b.call("resume")
            awaitPhase("277-event convergence after $point crash", 60_000) {
                caught(a.status(), SyncProcessFixture.EXPECTED_COUNT) && caught(b.status(), SyncProcessFixture.EXPECTED_COUNT)
            }
            val finalA = a.call("manifest").jsonObject
            val finalB = b.call("manifest").jsonObject
            val oracleDb = openIknaDatabase(root.resolve("oracle.db"))
            val expected = try {
                SyncProcessFixture.seed(oracleDb)
                val repo = SyncProcessFixture.repository(oracleDb)
                repo.mergeJournal(SyncProcessFixture.expected())
                SyncProcessFixture.manifest(oracleDb, repo)
            } finally { oracleDb.close() }
            assertEquals(SyncProcessFixture.EXPECTED_COUNT, expected.number("rows"))
            for (actual in listOf(finalA, finalB)) {
                assertFields(expected, actual, LEARNING_FIELDS)
                assertTrue(actual.flag("undoValid"))
                assertTrue(actual.flag("localIndexValid"))
                assertEquals(277L, actual.number("localIndexCount"))
                assertEquals(2, actual.getValue("undos").jsonArray.size)
                assertEquals(277L, actual.number("sourceSequence"))
                assertEquals(277L, actual.number("remoteSequence"))
                assertEquals(277L, actual.number("remoteIndexCount"))
                assertEquals(operation, actual.getValue("operation").jsonPrimitive.content)
            }
            assertEquals(finalA["sourcePrefix"], finalB["remotePrefix"])
            assertEquals(finalB["sourcePrefix"], finalA["remotePrefix"])
            val quietA = a.status(); val quietB = b.status()
            var lastA = quietA.number("lastSuccess"); var lastB = quietB.number("lastSuccess")
            repeat(3) {
                awaitPhase("duplicate no-op round", 15_000) {
                    val x = a.status(); val y = b.status()
                    if (caught(x, 277) && caught(y, 277) && x.number("lastSuccess") > lastA && y.number("lastSuccess") > lastB) {
                        lastA = x.number("lastSuccess"); lastB = y.number("lastSuccess"); true
                    } else false
                }
            }
            assertFields(quietA, a.status(), listOf("merged", "cardWrites", "rows"))
            assertFields(quietB, b.status(), listOf("merged", "cardWrites", "rows"))
            assertFields(finalA, a.call("manifest").jsonObject, DURABLE_PAGE_FIELDS + listOf("sourceSequence", "sourcePrefix", "localIndexCount", "localIndexDigest", "localIndexValid"))
            assertFields(finalB, b.call("manifest").jsonObject, DURABLE_PAGE_FIELDS + listOf("sourceSequence", "sourcePrefix", "localIndexCount", "localIndexDigest", "localIndexValid"))
            println("Process crash acceptance: point=$point, sharedZone=$fixtureZone, events=277, undoEdges=2, restartedPeers=2, noOpRounds=3")
        } finally {
            val interrupted = Thread.interrupted()
            val cleanupFailures = children.mapNotNull { child -> runCatching { child.kill() }.exceptionOrNull() }
            val allReaped = children.none { it.alive }
            if (allReaped) root.deleteRecursively()
            if (interrupted) Thread.currentThread().interrupt()
            assertTrue("All harness children must be reaped; cleanupFailures=${cleanupFailures.size}", allReaped && cleanupFailures.isEmpty())
        }
    }
    private fun caught(status: JsonObject, count: Long) = status.flag("complete") && status.flag("caughtUp") && status.number("rows") == count
    private fun awaitPhase(label: String, millis: Long, condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        while (System.nanoTime() < until) {
            if (condition()) {
                if (System.nanoTime() <= until) return
                break
            }
            Thread.sleep(50)
        }
        fail("Timed out during $label")
    }
    private fun assertFields(expected: JsonObject, actual: JsonObject, keys: List<String>) {
        keys.forEach { assertEquals("Field $it", expected[it], actual[it]) }
    }
    private class Child(home: File, fixtureZone: String) {
        private val sequence = AtomicInteger()
        private val replies = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
        private val ready = CompletableFuture<Long>()
        private val fault = CompletableFuture<JsonObject>()
        private val process: Process
        val pid: Long get() = process.pid()
        val alive: Boolean get() = process.isAlive
        init {
            val cpFile = File(requireNotNull(System.getProperty("ikna.test.runtimeClasspathFile")))
            val argfile = home.resolve("java.args")
            fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            val userHome = home.resolve("user-home").apply { mkdirs() }
            val temporary = home.resolve("tmp").apply { mkdirs() }
            argfile.writeText(listOf("-Xmx256m", "-XX:ActiveProcessorCount=1", "-Djava.awt.headless=true", quote("-Duser.timezone=$fixtureZone"),
                quote("-Duser.home=${userHome.absolutePath}"), quote("-Djava.io.tmpdir=${temporary.absolutePath}"), "-cp",
                quote(cpFile.readText()), SyncProcessPeer::class.java.name, quote(home.absolutePath)).joinToString("\n"))
            val java = File(System.getProperty("java.home"), "bin/" + if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            process = ProcessBuilder(java.absolutePath, "@${argfile.absolutePath}").apply {
                environment()["XDG_CONFIG_HOME"] = userHome.resolve("config").apply { mkdirs() }.absolutePath
                environment()["XDG_CACHE_HOME"] = userHome.resolve("cache").apply { mkdirs() }.absolutePath
                environment()["XDG_DATA_HOME"] = userHome.resolve("data").apply { mkdirs() }.absolutePath
            }.start()
            thread(name = "sync-peer-output-$pid", isDaemon = true) {
                try {
                    val input = process.inputStream.bufferedReader()
                    while (true) {
                        val text = StringBuilder()
                        var ended = false
                        while (true) {
                            val next = input.read()
                            if (next < 0) { ended = true; break }
                            if (next == 10) break
                            check(text.length < 65_536) { "Oversized child response" }
                            text.append(next.toChar())
                        }
                        if (ended && text.isEmpty()) break
                        val line = text.toString()
                        val response = Json.parseToJsonElement(line).jsonObject
                        when (response.getValue("kind").jsonPrimitive.content) {
                            "ready" -> ready.complete(response.getValue("pid").jsonPrimitive.long)
                            "fault" -> fault.complete(response)
                            "response" -> replies.remove(response.getValue("id").jsonPrimitive.int)?.complete(response)
                            else -> error("Unknown child response")
                        }
                    }
                    error("Child output closed")
                } catch (failure: Exception) {
                    val safe = IllegalStateException("Child IPC ended (${failure.javaClass.simpleName})")
                    ready.completeExceptionally(safe); fault.completeExceptionally(safe)
                    replies.values.forEach { it.completeExceptionally(safe) }
                }
            }
            // Drain but never retain arbitrary child runtime output in test logs.
            thread(name = "sync-peer-stderr-$pid", isDaemon = true) { process.errorStream.use { input ->
                val bytes = ByteArray(4096); while (input.read(bytes) >= 0) Unit
            } }
            try { assertEquals(pid, ready.get(15, TimeUnit.SECONDS)) }
            catch (failure: Throwable) {
                val interrupted = Thread.interrupted()
                runCatching { kill() }
                if (interrupted) Thread.currentThread().interrupt()
                throw failure
            }
        }
        fun call(command: String, vararg fields: Pair<String, JsonElement>): JsonElement {
            check(alive) { "Child exited before $command" }
            val id = sequence.incrementAndGet()
            val future = CompletableFuture<JsonObject>()
            replies[id] = future
            val request = buildJsonObject {
                put("id", id); put("command", command); fields.forEach { (key, value) -> put(key, value) }
            }.toString()
            check(request.length <= 65_536)
            process.outputStream.write((request + "\n").toByteArray()); process.outputStream.flush()
            val response = future.get(15, TimeUnit.SECONDS)
            check("failure" !in response) { "Child $command failed: ${response["failure"]?.jsonPrimitive?.content}" }
            return response.getValue("value")
        }
        fun status() = call("status").jsonObject
        fun awaitFault(timeout: Long) = fault.get(timeout, TimeUnit.MILLISECONDS)
        fun kill() {
            if (process.isAlive) process.destroyForcibly()
            check(process.waitFor(10, TimeUnit.SECONDS)) { "Harness child failed to exit" }
        }
    }
    companion object {
        private val LEARNING_FIELDS = listOf("rows", "journalDigest", "ids", "undos", "cards", "components", "stats")
        private val DURABLE_PAGE_FIELDS = LEARNING_FIELDS + listOf("undoValid", "streamId", "operation", "remoteSequence", "remotePrefix", "remoteIndexDigest", "remoteIndexCount")
        private fun JsonObject.flag(key: String) = getValue(key).jsonPrimitive.boolean
        private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.long
    }
}
