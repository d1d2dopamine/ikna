package dev.ikna.sync

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.util.EnumSet

/** App-private sync state only. The enclosing application home must already be trusted. */
internal class PrivateSyncFiles(
    private val permissions: PrivateSyncPermissions = PrivateSyncPermissions(),
    private val replace: (Path, Path) -> Unit = { from, to ->
        Files.move(from, to, ATOMIC_MOVE, REPLACE_EXISTING)
    },
) {
    fun directory(file: File) {
        val path = file.toPath()
        var created = false
        if (!Files.exists(path, NOFOLLOW_LINKS)) {
            val parent = requireNotNull(file.absoluteFile.parentFile) { "Private state requires an explicit directory" }
            // Only missing ancestors are created. Never chmod the user's existing application home.
            if (!Files.exists(parent.toPath(), NOFOLLOW_LINKS)) directory(parent)
            permissions.requireKind(parent.toPath(), true)
            Files.createDirectory(path, permissions.creationAttribute(path, true))
            created = true
        }
        permissions.restrict(path, true, created)
    }

    fun read(file: File, maxBytes: Long): String {
        require(maxBytes in 0..Int.MAX_VALUE.toLong())
        directory(requireNotNull(file.parentFile) { "Private state requires an explicit directory" })
        permissions.restrict(file.toPath(), false)
        require(Files.size(file.toPath()) <= maxBytes) { "Private state exceeds its size limit" }
        // Bound the actual read too, and reject a link substituted after the attribute check.
        return Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(bytes.size().toLong() + count <= maxBytes) { "Private state exceeds its size limit" }
                bytes.write(buffer, 0, count)
            }
            bytes.toString(Charsets.UTF_8.name())
        }
    }

    fun write(file: File, value: String) {
        val parent = requireNotNull(file.parentFile) { "Private state requires an explicit directory" }
        directory(parent)
        val target = file.toPath()
        if (Files.exists(target, NOFOLLOW_LINKS)) permissions.restrict(target, false)
        // Attribute application is atomic with creation. No broad/default-ACL temp is used.
        val temp = Files.createTempFile(parent.toPath(), ".ikna-", ".tmp",
            permissions.creationAttribute(parent.toPath(), false))
        try {
            permissions.restrict(temp, false, newlyCreated = true)
            FileChannel.open(temp, WRITE, NOFOLLOW_LINKS).use { channel ->
                val bytes = ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            // Recheck before publication, then fail closed if atomic replacement is unavailable.
            permissions.restrict(parent.toPath(), true)
            permissions.restrict(temp, false)
            replace(temp, target)
            permissions.restrict(target, false)
            // Directory channels/fsync are not supported on every provider, notably Windows.
            // Only this durability limitation is best-effort; permission failures always propagate.
            try {
                FileChannel.open(parent.toPath(), READ).use { it.force(true) }
            } catch (_: java.io.IOException) {
                // The synced atomic file is retained; universal power-loss durability is not claimed.
            } catch (_: UnsupportedOperationException) {
                // Same limitation for providers without directory channels.
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

/** Real NIO views, with a small seam for deterministic unsupported/denied/readback tests. */
internal class PrivateSyncPermissions(private val views: Views = NioViews) {
    internal interface Views {
        fun basic(path: Path): BasicFileAttributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        fun posix(path: Path): PosixFileAttributeView?
        fun acl(path: Path): AclFileAttributeView?
        fun currentUser(path: Path): UserPrincipal
    }

    private object NioViews : Views {
        override fun posix(path: Path): PosixFileAttributeView? =
            Files.getFileAttributeView(path, PosixFileAttributeView::class.java, NOFOLLOW_LINKS)
        override fun acl(path: Path): AclFileAttributeView? =
            Files.getFileAttributeView(path, AclFileAttributeView::class.java, NOFOLLOW_LINKS)
        override fun currentUser(path: Path): UserPrincipal {
            val name = System.getProperty("user.name")?.takeIf { it.isNotBlank() }
                ?: error("Cannot identify the current account for private sync state")
            return path.fileSystem.userPrincipalLookupService.lookupPrincipalByName(name)
        }
    }

    fun creationAttribute(path: Path, directory: Boolean): FileAttribute<*> {
        if (views.posix(path) != null) return PosixFilePermissions.asFileAttribute(mode(directory))
        checkNotNull(views.acl(path)) { "Private sync permissions are unsupported: $path" }
        val acl = ownerAcl(currentUser(path), directory)
        return object : FileAttribute<List<AclEntry>> {
            override fun name() = "acl:acl"
            override fun value() = acl
        }
    }

    fun requireKind(path: Path, directory: Boolean) {
        val attributes = views.basic(path)
        // Windows directory junctions can report isDirectory=true and isSymbolicLink=false;
        // the NIO provider marks other reparse points as isOther. Do not chmod through either.
        check(!attributes.isSymbolicLink && !attributes.isOther &&
            (if (directory) attributes.isDirectory else attributes.isRegularFile)) {
            "Private sync state must be a real ${if (directory) "directory" else "file"}: $path"
        }
    }

    fun restrict(path: Path, directory: Boolean, newlyCreated: Boolean = false) {
        requireKind(path, directory)
        val posix = views.posix(path)
        if (posix != null) {
            val expected = mode(directory)
            val owner = posix.readAttributes().owner()
            posix.setPermissions(expected)
            val actual = posix.readAttributes()
            check(actual.permissions() == expected && actual.owner() == owner) {
                "Private sync POSIX permissions could not be verified: $path"
            }
            return
        }
        val acl = checkNotNull(views.acl(path)) { "Private sync permissions are unsupported: $path" }
        val owner = currentUser(path)
        if (newlyCreated && acl.owner != owner) {
            // Elevated Windows can give new files a group owner. Initialize only our new empty
            // objects; never take ownership of an existing foreign/group-owned private directory.
            acl.owner = owner
        }
        check(acl.owner == owner) {
            "Private sync state is owned by another account or group: $path. Use a private directory owned by the current account."
        }
        val expected = ownerAcl(owner, directory)
        acl.acl = expected
        // Windows NIO does not promise a permanently protected DACL. Verify exact readback on
        // every access; any residual inherited/broad ACE or changed ownership is a hard failure.
        check(acl.owner == owner && acl.acl == expected) {
            "Private sync owner-only ACL could not be verified: $path"
        }
    }

    private fun currentUser(path: Path): UserPrincipal = views.currentUser(path).also {
        check(it !is GroupPrincipal) { "Private sync requires an individual account, not a group: $path" }
    }

    private fun mode(directory: Boolean): Set<PosixFilePermission> = buildSet {
        add(PosixFilePermission.OWNER_READ)
        add(PosixFilePermission.OWNER_WRITE)
        if (directory) add(PosixFilePermission.OWNER_EXECUTE)
    }

    private fun ownerAcl(owner: UserPrincipal, directory: Boolean): List<AclEntry> = listOf(
        AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
            .setFlags(if (directory) setOf(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT) else emptySet())
            .build()
    )
}
