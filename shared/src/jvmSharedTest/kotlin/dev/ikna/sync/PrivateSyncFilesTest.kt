package dev.ikna.sync

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateSyncFilesTest {
    private data class User(private val value: String) : UserPrincipal { override fun getName() = value }
    private data class Group(private val value: String) : GroupPrincipal { override fun getName() = value }
    private val user = User("synthetic-owner")
    private val stranger = User("synthetic-other")
    private fun entry(principal: UserPrincipal) = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
        .setPrincipal(principal).setPermissions(AclEntryPermission.READ_DATA).build()
    private class Acl(var principal: UserPrincipal, var entries: List<AclEntry>) : AclFileAttributeView {
        var onSet: (List<AclEntry>) -> List<AclEntry> = { it }
        var onRead: () -> Unit = {}
        var sets = 0
        var ownerSets = 0
        override fun name() = "acl"
        override fun getOwner() = principal
        override fun setOwner(owner: UserPrincipal) { ownerSets++; principal = owner }
        override fun getAcl(): List<AclEntry> { onRead(); return entries }
        override fun setAcl(acl: List<AclEntry>) { sets++; entries = onSet(acl) }
    }
    private class Views(val view: Acl?, val user: UserPrincipal) : PrivateSyncPermissions.Views {
        override fun posix(path: Path): PosixFileAttributeView? = null
        override fun acl(path: Path): AclFileAttributeView? = view
        override fun currentUser(path: Path) = user
    }
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("ikna-private-permission-test-")
        try { action(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test fun `ACL replaces broad entries with exactly one individual owner allow`() = fixture { root ->
        val acl = Acl(user, listOf(entry(stranger), entry(user)))
        PrivateSyncPermissions(Views(acl, user)).restrict(root, true)
        assertEquals(1, acl.entries.size)
        val actual = acl.entries.single()
        assertEquals(user, actual.principal())
        assertEquals(AclEntryType.ALLOW, actual.type())
        assertEquals(AclEntryPermission.values().toSet(), actual.permissions())
        assertEquals(setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT), actual.flags())
        assertEquals(0, acl.ownerSets)
    }

    @Test fun `file creation attribute has no inherited directory flags`() = fixture { root ->
        val policy = PrivateSyncPermissions(Views(Acl(user, emptyList()), user))
        val attribute = policy.creationAttribute(root, false)
        assertEquals("acl:acl", attribute.name())
        val entry = (attribute.value() as List<*>).single() as AclEntry
        assertEquals(user, entry.principal())
        assertTrue(entry.flags().isEmpty())
        assertEquals(AclEntryPermission.values().toSet(), entry.permissions())
    }

    @Test fun `residual broad inherited entry fails readback`() = fixture { root ->
        val acl = Acl(user, emptyList()).apply { onSet = { it + entry(stranger) } }
        assertFails { PrivateSyncPermissions(Views(acl, user)).restrict(root, true) }
    }

    @Test fun `missing owner rights and inherit-only ACL fail readback`() = fixture { root ->
        listOf(
            entry(user),
            AclEntry.newBuilder(entry(user)).setFlags(AclEntryFlag.INHERIT_ONLY).build(),
        ).forEach { altered ->
            val acl = Acl(user, emptyList()).apply { onSet = { listOf(altered) } }
            assertFails { PrivateSyncPermissions(Views(acl, user)).restrict(root, true) }
        }
    }

    @Test fun `ACL write and read failures propagate`() = fixture { root ->
        val denied = Acl(user, emptyList()).apply { onSet = { throw IOException("synthetic set denied") } }
        assertFails { PrivateSyncPermissions(Views(denied, user)).restrict(root, true) }
        val unreadable = Acl(user, emptyList()).apply { onRead = { throw IOException("synthetic read denied") } }
        assertFails { PrivateSyncPermissions(Views(unreadable, user)).restrict(root, true) }
    }

    @Test fun `unsupported provider has no java io fallback`() = fixture { root ->
        val policy = PrivateSyncPermissions(Views(null, user))
        assertFails { policy.restrict(root, true) }
        assertFails { policy.creationAttribute(root, false) }
    }

    @Test fun `foreign and group owners are never silently taken over`() = fixture { root ->
        listOf<UserPrincipal>(stranger, Group("synthetic-admins")).forEach { owner ->
            val acl = Acl(owner, listOf(entry(owner)))
            assertFails { PrivateSyncPermissions(Views(acl, user)).restrict(root, true) }
            assertEquals(0, acl.sets)
            assertEquals(0, acl.ownerSets)
        }
    }

    @Test fun `group cannot be resolved as the current individual account`() = fixture { root ->
        val group = Group("synthetic-admins")
        val acl = Acl(group, emptyList())
        val policy = PrivateSyncPermissions(Views(acl, group))
        assertFails { policy.restrict(root, true, newlyCreated = true) }
        assertFails { policy.creationAttribute(root, false) }
        assertEquals(0, acl.ownerSets)
        assertEquals(0, acl.sets)
    }

    @Test fun `new empty object can initialize owner but owner readback must match`() = fixture { root ->
        val acl = Acl(Group("synthetic-admins"), emptyList())
        PrivateSyncPermissions(Views(acl, user)).restrict(root, true, newlyCreated = true)
        assertEquals(user, acl.principal)
        assertEquals(1, acl.ownerSets)
        val changed = Acl(user, emptyList()).apply { onSet = { principal = stranger; it } }
        assertFails { PrivateSyncPermissions(Views(changed, user)).restrict(root, true) }
    }

    @Test fun `permission failure stops before temp or secret bytes`() = fixture { root ->
        val target = root.resolve("state.json").toFile().apply { writeText("old-state") }
        val acl = Acl(user, emptyList()).apply { onSet = { throw IOException("synthetic denied") } }
        val files = PrivateSyncFiles(PrivateSyncPermissions(Views(acl, user)))
        assertFails { files.write(target, "new-secret") }
        assertEquals("old-state", target.readText())
        assertEquals(listOf("state.json"), root.toFile().list()!!.toList())
    }

    @Test fun `unsupported atomic replacement keeps old content and cleans temp`() = fixture { root ->
        val state = root.resolve("private").resolve("state.json").toFile()
        val normal = PrivateSyncFiles()
        normal.write(state, "old-state")
        val files = PrivateSyncFiles(replace = { from, to ->
            throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "synthetic provider")
        })
        assertFails { files.write(state, "new-secret") }
        assertEquals("old-state", normal.read(state, 100))
        assertEquals(listOf("state.json"), state.parentFile.list()!!.toList())
    }

    @Test fun `actual provider writes replaces and reads bounded private state`() = fixture { root ->
        val directory = root.resolve("private").toFile()
        val state = directory.resolve("state.json")
        val files = PrivateSyncFiles()
        files.write(state, "synthetic-first")
        files.write(state, "synthetic-replacement")
        assertEquals("synthetic-replacement", files.read(state, 21))
        assertFails { files.read(state, 20) }
        assertEquals(listOf("state.json"), directory.list()!!.toList())
        val posix = Files.getFileAttributeView(state.toPath(), PosixFileAttributeView::class.java)
        if (posix != null) {
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), posix.readAttributes().permissions())
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(directory.toPath()))
        }
    }

    @Test fun `permission denial blocks existing reads before exposing content`() = fixture { root ->
        val target = root.resolve("state").toFile().apply { writeText("synthetic-private") }
        val denied = Acl(user, emptyList()).apply { onSet = { throw IOException("synthetic denied") } }
        assertFails { PrivateSyncFiles(PrivateSyncPermissions(Views(denied, user))).read(target, 100) }
    }

    @Test fun `directory and nonregular file are refused as state`() = fixture { root ->
        val files = PrivateSyncFiles()
        val directory = root.resolve("private").toFile().apply { mkdir() }
        val childDirectory = directory.resolve("child").apply { mkdir() }
        assertFails { files.read(childDirectory, 100) }
        assertFails { files.write(childDirectory, "synthetic") }
        val file = root.resolve("file").toFile().apply { writeText("unchanged") }
        assertFails { files.directory(file) }
        assertEquals("unchanged", file.readText())
    }

    @Test fun `new empty temp permission failure preserves prior state and removes temp`() = fixture { root ->
        if (Files.getFileAttributeView(root, PosixFileAttributeView::class.java) == null) return@fixture
        val directory = root.resolve("private").toFile()
        val target = directory.resolve("state.json")
        PrivateSyncFiles().write(target, "old-state")
        var observedEmptyTemp = false
        val views = object : PrivateSyncPermissions.Views {
            override fun posix(path: Path): PosixFileAttributeView {
                val real = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
                return if (!path.fileName.toString().startsWith(".ikna-")) real else
                    object : PosixFileAttributeView by real {
                        override fun setPermissions(permissions: Set<PosixFilePermission>) {
                            observedEmptyTemp = Files.size(path) == 0L
                            throw IOException("synthetic temp permission failure")
                        }
                    }
            }
            override fun acl(path: Path): AclFileAttributeView? = error("must not fall back after POSIX failure")
            override fun currentUser(path: Path): UserPrincipal = error("POSIX does not use account lookup")
        }
        assertFails { PrivateSyncFiles(PrivateSyncPermissions(views)).write(target, "new-secret") }
        assertTrue(observedEmptyTemp)
        assertEquals("old-state", target.readText())
        assertEquals(listOf("state.json"), directory.list()!!.toList())
    }

    @Test fun `reparse directory attributes are rejected before permission mutation or child creation`() = fixture { root ->
        val actual = Files.readAttributes(root, BasicFileAttributes::class.java)
        val acl = Acl(user, emptyList())
        val views = object : PrivateSyncPermissions.Views {
            override fun basic(path: Path): BasicFileAttributes = object : BasicFileAttributes by actual {
                override fun isDirectory() = true
                override fun isSymbolicLink() = false
                override fun isOther() = true
            }
            override fun posix(path: Path): PosixFileAttributeView? = null
            override fun acl(path: Path): AclFileAttributeView = acl
            override fun currentUser(path: Path): UserPrincipal = user
        }
        val policy = PrivateSyncPermissions(views)
        assertFails { policy.restrict(root, true) }
        assertFails { PrivateSyncFiles(policy).directory(root.resolve("child").toFile()) }
        assertEquals(0, acl.sets)
        assertFalse(Files.exists(root.resolve("child")))
    }

    @Test fun `actual provider protects identity before loading the existing key`() = fixture { root ->
        val directory = root.resolve("transport").toFile()
        val first = LanSyncSecurity.identity(directory)
        val identity = directory.resolve("identity.pem")
        val posix = Files.getFileAttributeView(identity.toPath(), PosixFileAttributeView::class.java)
        if (posix != null) Files.setPosixFilePermissions(identity.toPath(), setOf(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_READ))
        val second = LanSyncSecurity.identity(directory)
        assertEquals(LanSyncSecurity.id(first.certificate), LanSyncSecurity.id(second.certificate))
        if (posix != null) assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            posix.readAttributes().permissions())
    }

    @Test fun `POSIX symlink file and directory are rejected without changing target`() = fixture { root ->
        // Windows symlink creation needs OS privileges; native ACL coverage is in desktopTest.
        if (Files.getFileAttributeView(root, PosixFileAttributeView::class.java) == null) return@fixture
        val outside = root.resolve("outside").toFile().apply { writeText("unchanged") }
        val private = root.resolve("private").toFile()
        val files = PrivateSyncFiles()
        files.directory(private)
        val link = private.toPath().resolve("state")
        Files.createSymbolicLink(link, outside.toPath())
        val mode = Files.getPosixFilePermissions(outside.toPath())
        assertFails { files.read(link.toFile(), 100) }
        assertFails { files.write(link.toFile(), "new-secret") }
        assertEquals("unchanged", outside.readText())
        assertEquals(mode, Files.getPosixFilePermissions(outside.toPath()))
        val directoryLink = root.resolve("linked-directory")
        Files.createSymbolicLink(directoryLink, private.toPath())
        assertFails { files.directory(directoryLink.toFile()) }
        assertFalse(private.resolve("state.json").exists())
    }
}
