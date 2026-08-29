package dev.ikna.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The schema history is a data-safety file, not a build artifact.
 *
 * Room writes one JSON file per database version into `app/schemas` during every
 * build, and those files are the only record of what the database used to look
 * like. Without version 2 on disk there is nothing to migrate a version 2
 * database from, and there is no way to tell an upgrade that preserves a user's
 * four months of answers from one that quietly drops them — both compile, both
 * install, and only one of them is a disaster.
 *
 * Until 0.6.1 the folder was never committed. CI uploaded it as an artifact,
 * which helps nobody in six months' time.
 *
 * What this test can and cannot do is worth being honest about. It cannot notice
 * that the folder is missing from the repository, because a build regenerates the
 * current version's file before any test runs; the CI step named "Schemas are
 * committed" does that part, by asking git. What it does check is that the schema
 * on disk agrees with the migration that is supposed to produce it, and that
 * every version step has a migration at all.
 */
private const val DB_VERSION = 5

private const val SCHEMA_DIR = "schemas/dev.ikna.data.db.IknaDatabase"

class SchemaTest {

	@Test
	fun `the schema of the current version is on disk`() {
		val file = schema(DB_VERSION)
		assertNotNull(
			"app/$SCHEMA_DIR/$DB_VERSION.json is missing. Room writes it during a " +
				"build; commit whatever appears there. Without it, the next version " +
				"has nothing to be compared against.",
			file
		)
		assertTrue(
			"$DB_VERSION.json does not declare version $DB_VERSION.",
			file!!.readText().contains("\"version\": $DB_VERSION")
		)
	}

	@Test
	fun `the column the last migration adds is in the schema it produces`() {
		val text = schema(3)?.readText() ?: return
		assertTrue(
			"daily_stats is not in the version 3 schema at all.",
			text.contains("daily_stats")
		)
		assertTrue(
			"correctCount is missing from the version 3 schema, but MIGRATION_2_3 " +
				"adds that column. One of the two is wrong, and the migration is the " +
				"one that runs on other people's phones.",
			text.contains("correctCount")
		)
	}

	@Test
	fun `the columns the governor migration adds are in the schema it produces`() {
		// Skips until a build has written 4.json. That file carries an identity
		// hash computed by Room from the entities, so it cannot be written by
		// hand -- and a hand-made one would be worse than none, because the
		// next reader would have no way to tell it apart from Room's.
		val text = schema(4)?.readText() ?: return
		val added = listOf(
			"activityRatio", "daysSinceStart", "cleanDays",
			"newIntroducedLastWeek", "totalReviews", "daysSinceReturn",
			"overheated", "newCeiling", "gate"
		)
		for (column in added) {
			assertTrue(
				"$column is missing from the version 4 schema, but MIGRATION_3_4 " +
					"adds it. One of the two is wrong, and the migration is the one " +
					"that runs on other people's phones.",
				text.contains(column)
			)
		}
	}

	@Test
	fun `the columns the newest migration adds are in the schema it produces`() {
		// Skips until a build has written 5.json, for the same reason as above:
		// Room computes an identity hash into it from the entities, so the file
		// appears when the project is built and must not be typed out by hand.
		val text = schema(5)?.readText() ?: return
		for (column in listOf("ipa", "ipaContext")) {
			assertTrue(
				"$column is missing from the version 5 schema, but MIGRATION_4_5 " +
					"adds it. One of the two is wrong, and the migration is the one " +
					"that runs on other people's phones.",
				text.contains(column)
			)
		}
	}

	@Test
	fun `the transcription columns are nullable`() {
		// The whole reason an upgrade is free: nothing is rewritten and nothing
		// is recomputed, because a chunk installed before this release is
		// allowed to have no transcription at all. A NOT NULL here would mean
		// inventing a value for every row already on every phone.
		val src = source("src/main/java/dev/ikna/data/db/Entities.kt")
		assertNotNull("Entities.kt was not found from the test's directory.", src)
		val text = src!!.readText()
		assertTrue(
			"ChunkEntity.ipa is not a nullable column with a default.",
			text.contains("val ipa: String? = null")
		)
		assertTrue(
			"ChunkEntity.ipaContext is not a nullable column with a default.",
			text.contains("val ipaContext: String? = null")
		)
	}

	@Test
	fun `the version before it did not have that column`() {
		// Only checkable once a version 2 schema exists in the repository. It was
		// never committed while version 2 was current, and a build of this code
		// cannot produce it, so the check skips rather than failing for something
		// nobody can fix now.
		val text = schema(2)?.readText() ?: return
		assertFalse(
			"correctCount is already in the version 2 schema, so MIGRATION_2_3 " +
				"would try to add a column that exists and every upgrade from 2 " +
				"would crash.",
			text.contains("correctCount")
		)
	}

	@Test
	fun `every step between versions has a migration`() {
		val src = source("src/main/java/dev/ikna/data/db/Migrations.kt")
		assertNotNull("Migrations.kt was not found from the test's directory.", src)
		val steps = Regex("""Migration\((\d+),\s*(\d+)\)""")
			.findAll(src!!.readText())
			.map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
			.toSet()
		for (from in 1 until DB_VERSION) {
			assertTrue(
				"No migration from $from to ${from + 1}. A gap here means an upgrade " +
					"from that version cannot happen, and destructive fallback is " +
					"banned in this project, so the app will refuse to open.",
				(from to from + 1) in steps
			)
		}
	}

	@Test
	fun `the database is the version this test was written for`() {
		val src = source("src/main/java/dev/ikna/data/db/IknaDatabase.kt")
		assertNotNull("IknaDatabase.kt was not found from the test's directory.", src)
		assertTrue(
			"IknaDatabase is no longer version $DB_VERSION. Raise DB_VERSION in this " +
				"test to match, which is also the reminder to commit the new schema " +
				"file and to write the migration that reaches it.",
				src!!.readText().contains("version = $DB_VERSION")
			)
			assertTrue(
				"The diagnostic database version must move with Room's version.",
				src!!.readText().contains("const val IKNA_DATABASE_VERSION = $DB_VERSION")
			)
		}

	private fun schema(version: Int): File? = find("$SCHEMA_DIR/$version.json")

	private fun source(path: String): File? = find(path)

	/**
	 * Gradle runs unit tests from the module directory, so plain relative paths
	 * work; the `app/` prefix is there so that running them from the repository
	 * root finds the same files instead of silently skipping every check.
	 */
	private fun find(path: String): File? =
		listOf(File(path), File("app/$path")).firstOrNull { it.isFile }
}
