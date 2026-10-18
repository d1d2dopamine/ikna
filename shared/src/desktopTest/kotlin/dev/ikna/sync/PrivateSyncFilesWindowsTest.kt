package dev.ikna.sync

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.GroupPrincipal
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** CI must run this class on windows-latest; provider doubles are not Windows acceptance. */
class PrivateSyncFilesWindowsTest {
    private fun windows(action: (Path) -> Unit) {
        assumeTrue("Windows NIO ACL provider required", System.getProperty("os.name").startsWith("Windows"))
        val root = Files.createTempDirectory("ikna-native-private-acl-")
        try { action(root) } finally { root.toFile().deleteRecursively() }
    }

    private fun command(vararg arguments: String): String {
        val process = ProcessBuilder(*arguments).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Synthetic Windows fixture command timed out")
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertEquals(0, process.exitValue(), output)
            return output.trim()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun assertPrivate(path: Path, directory: Boolean) {
        val view = assertNotNull(Files.getFileAttributeView(path, AclFileAttributeView::class.java, NOFOLLOW_LINKS))
        val user = path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(System.getProperty("user.name"))
        assertFalse(user is GroupPrincipal)
        assertEquals(user, view.owner)
        val entry = view.acl.single()
        assertEquals(AclEntryType.ALLOW, entry.type())
        assertEquals(user, entry.principal())
        assertEquals(AclEntryPermission.values().toSet(), entry.permissions())
        assertEquals(if (directory) setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT) else emptySet(), entry.flags())
    }

    @Test fun `native Windows ACL secures inherited directory and atomic replacement`() = windows { root ->
        // Only a synthetic fixture is broadened to reproduce inherited access. Never touch home.
        val parent = assertNotNull(Files.getFileAttributeView(root, AclFileAttributeView::class.java))
        // Resolve the well-known SID through Windows, so the fixture also works on localized OSes.
        val everyoneName = command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); " +
                "([System.Security.Principal.SecurityIdentifier]'S-1-1-0').Translate([System.Security.Principal.NTAccount]).Value")
        val everyone = root.fileSystem.userPrincipalLookupService.lookupPrincipalByName(everyoneName)
        val broad = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone)
            .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ATTRIBUTES)
            .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build()
        parent.acl = parent.acl + broad
        assertTrue(parent.acl.any { it.principal() == everyone && it.type() == AclEntryType.ALLOW })
        val directory = root.resolve("private").toFile()
        val state = directory.resolve("state.json")
        LanSyncSecurity.writePrivate(state, "synthetic-first")
        assertPrivate(directory.toPath(), true)
        assertPrivate(state.toPath(), false)
        // Existing file permissions are resecured before a read, rather than trusting the parent.
        val stateView = Files.getFileAttributeView(state.toPath(), AclFileAttributeView::class.java)
        stateView.acl = stateView.acl + AclEntry.newBuilder(broad).setFlags(emptySet()).build()
        assertEquals("synthetic-first", LanSyncSecurity.readPrivate(state, 100))
        assertPrivate(state.toPath(), false)
        LanSyncSecurity.writePrivate(state, "synthetic-replacement")
        assertEquals("synthetic-replacement", LanSyncSecurity.readPrivate(state, 100))
        assertPrivate(state.toPath(), false)
        assertEquals(listOf("state.json"), directory.list()!!.toList())
    }

    @Test fun `native Windows identity survives reload under verified ACL`() = windows { root ->
        val directory = root.resolve("transport").toFile()
        val first = LanSyncSecurity.identity(directory)
        val state = directory.resolve("identity.pem")
        assertPrivate(directory.toPath(), true)
        assertPrivate(state.toPath(), false)
        val reloaded = LanSyncSecurity.identity(directory)
        assertEquals(LanSyncSecurity.id(first.certificate), LanSyncSecurity.id(reloaded.certificate))
        assertPrivate(state.toPath(), false)
        assertEquals(listOf("identity.pem"), directory.list()!!.toList())
    }

    @Test fun `native Windows junction is rejected without changing destination ACL`() = windows { root ->
        val destination = root.resolve("destination")
        LanSyncSecurity.privateDirectory(destination.toFile())
        val view = Files.getFileAttributeView(destination, AclFileAttributeView::class.java)
        val before = view.acl
        val link = root.resolve("junction")
        command("cmd.exe", "/d", "/c", "mklink", "/J", link.toString(), destination.toString())
        try {
            assertFails { LanSyncSecurity.privateDirectory(link.toFile()) }
            assertFails { LanSyncSecurity.privateDirectory(link.resolve("child").toFile()) }
            assertEquals(before, view.acl)
            assertFalse(Files.exists(destination.resolve("child")))
        } finally {
            // Delete the junction itself, never recursively walk its destination.
            Files.deleteIfExists(link)
        }
    }

}
