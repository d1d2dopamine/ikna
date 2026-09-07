#!/usr/bin/env python3
"""Standard-library structural and real-SQLite checks for schema 7 and CI wiring.

These complement, not replace, Kotlin/JUnit and Android execution in grading.yml.
"""
from pathlib import Path
import json
import re
import sqlite3
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from check_grading import schema, identity_hash, create_database, seed_old_reviews, migration_sql

SHARED = ROOT / "shared/src/jvmShared/kotlin/dev/ikna"
FIELDS = {"inputRating": "INTEGER", "gradingVersion": "INTEGER", "gradingReason": "TEXT",
          "presentationLength": "INTEGER", "inputMethod": "TEXT", "peekSemantics": "TEXT"}


def new_sql():
    source = (SHARED / "data/db/Migrations.kt").read_text()
    body = source.split("private val MIGRATION_6_7 =", 1)[1].split("private val MIGRATION_7_8", 1)[0].split("val ALL:", 1)[0]
    return re.findall(r'connection\.execSQL\("([^"]+)"\)', body)


class FullGradingChecks(unittest.TestCase):
    def test_schema_seven_identity_and_additive_shape(self):
        old, new = schema(6), schema(7)
        self.assertEqual(7, new["version"])
        self.assertEqual(identity_hash(new), new["identityHash"])
        self.assertIn(new["identityHash"], new["setupQueries"][1])
        self.assertEqual(new_sql(), [f"ALTER TABLE reviews ADD COLUMN {name} {kind}" for name, kind in FIELDS.items()])
        for before, after in zip(old["entities"], new["entities"], strict=True):
            if before["tableName"] != "reviews":
                self.assertEqual(before, after)
            else:
                self.assertEqual(before["fields"], after["fields"][:-6])
                for (name, kind), field in zip(FIELDS.items(), after["fields"][-6:], strict=True):
                    self.assertEqual(dict(fieldPath=name, columnName=name, affinity=kind, notNull=False), field)
                self.assertEqual(before["indices"], after["indices"])

    def test_both_previous_versions_migrate_to_identical_fresh_schema(self):
        fresh = create_database(7)
        for version in (5, 6):
            db = create_database(version)
            seed_old_reviews(db)
            if version == 6:
                db.execute("UPDATE reviews SET latencyMs=900,swipeVelocityX=-1250.5,peeked=1,timingDiscardReason='focus_lost' WHERE id=41")
            columns = [r[1] for r in db.execute("PRAGMA table_info(reviews)")]
            old_columns = ','.join('"' + c + '"' for c in columns)
            old = db.execute(f"SELECT {old_columns} FROM reviews ORDER BY id").fetchall()
            if version == 5:
                for sql in migration_sql(): db.execute(sql)
            for sql in new_sql(): db.execute(sql)
            self.assertEqual(old, db.execute(f"SELECT {old_columns} FROM reviews ORDER BY id").fetchall())
            for entity in schema(7)["entities"]:
                for pragma in ("table_info", "index_list", "foreign_key_list"):
                    query = f'PRAGMA {pragma}("{entity["tableName"]}")'
                    self.assertEqual(fresh.execute(query).fetchall(), db.execute(query).fetchall())
            self.assertEqual([(None,) * 6] * 2, db.execute("SELECT " + ','.join(FIELDS) + " FROM reviews").fetchall())
            self.assertEqual(("ok",), db.execute("PRAGMA integrity_check").fetchone())
            db.close()
        fresh.close()

    def test_context_export_mappings_are_complete(self):
        record = (SHARED / "data/export/ReviewRecord.kt").read_text()
        for name in FIELDS:
            self.assertIn(f"{name} = {name}", record)
            self.assertIn(f"{name} = r.{name}", record)
        restore = (SHARED / "data/repo/RestoreRepository.kt").read_text()
        self.assertIn("rec == null || rec.synthetic", restore)
        self.assertLess(restore.index("Unsupported derived grading version"), restore.index("insertRecords(answers"))
        self.assertIn("if (records.isEmpty()) return RestoreResult", restore)

    def test_persisted_window_filters_undo_future_manual_and_discarded_rows(self):
        # Execute the actual DAO SQL assembled from its Kotlin string literals.
        source = (SHARED / "data/db/Daos.kt").read_text()
        method = source.index("suspend fun recentGradingTimings")
        start = source.rfind("@Query(", 0, method)
        expression = source[start:method]
        fragments = re.findall(r'"([^"\n]*)"', expression)
        sql = fragments[0] + "rating > 0 AND id NOT IN (SELECT undoOf FROM reviews WHERE undoOf IS NOT NULL)" + ''.join(fragments[1:])
        db = create_database(7)
        for i in range(1, 221):
            db.execute("""INSERT INTO reviews (id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,
                stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,inputRating,
                inputMethod,latencyMs,swipeVelocityX,presentationLength)
                VALUES (?, 'one', 0, ?, 3, 0, 1, 1, 5, 5, 3000, 0, 3, 'swipe', ?, 900, 25)""", (i, i, i + 1000))
        db.execute("UPDATE reviews SET timingDiscardReason='focus_lost' WHERE id=219")
        db.execute("UPDATE reviews SET inputMethod='keyboard' WHERE id=218")
        db.execute("UPDATE reviews SET swipeVelocityX=NULL WHERE id=217")
        db.execute("""INSERT INTO reviews (id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,
            stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,undoOf)
            VALUES (500,'one',0,221,0,0,0,0,0,0,0,0,216)""")
        db.row_factory = sqlite3.Row
        rows = db.execute(sql, dict(limit=200, beforeTs=219)).fetchall()
        ids = [row['id'] for row in rows]
        self.assertEqual(200, len(ids))
        self.assertEqual(215, ids[0])
        self.assertEqual(16, ids[-1])
        self.assertFalse(set(ids) & {216, 217, 218, 219, 220, 500})
        db.close()

    def test_live_restore_and_undo_use_input_outcome_not_derived_hard_as_failure(self):
        live = (SHARED / "data/repo/LearningRepository.kt").read_text()
        self.assertIn("recent.count { it.outcomeRating >= 3 }", live)
        self.assertIn("if (review.outcomeRating >= 3)", live)
        self.assertIn("components.recordAnswer(sessionCard.chunk.id, rating.value, now)", live)
        self.assertIn("bumpDailyStat(now, rating, durationMs)", live)
        for name in ("RestoreRepository", "ComponentRepository"):
            self.assertIn("if (r.outcomeRating >= 3)", (SHARED / f"data/repo/{name}.kt").read_text())
        for name in ("RestoreRepository", "SchedulerMigration"):
            self.assertIn("scheduler.applyRecordedReview", (SHARED / f"data/repo/{name}.kt").read_text())

    def test_legacy_preference_cannot_override_automatic_policy(self):
        settings = (SHARED / "data/prefs/SettingsStore.kt").read_text()
        self.assertIn("val derivedGrading: Boolean = false", settings)
        self.assertIn("derivedGrading = p[Keys.derivedGrading] ?: false", settings)
        self.assertIn("store.setDerivedGrading(false)", (SHARED / "data/export/SettingsBackup.kt").read_text())
        for path in ("app/src/main/java/dev/ikna/AppContainer.kt", "desktop/src/main/kotlin/dev/ikna/desktop/DesktopContainer.kt"):
            self.assertIn("derivedGradingEnabled = { dev.ikna.domain.optimizer.AutomaticLearningPolicy.DERIVED_WHEN_READY }", (ROOT / path).read_text())

    def test_ci_runs_production_kotlin_and_real_android_migrations(self):
        workflow = (ROOT / ".github/workflows/grading.yml").read_text()
        for token in (":desktop:test", ":app:testReleaseUnitTest", ":desktop:gradingLab", ":app:connectedDebugAndroidTest", "-Pikna.abi=emulator", "check_reports.py"):
            self.assertIn(token, workflow)
        for file in ("build.yml", "release.yml"):
            text = (ROOT / ".github/workflows" / file).read_text()
            self.assertIn("uses: ./.github/workflows/grading.yml", text)
            self.assertIn("needs: grading", text)

    def test_synthetic_fixtures_are_explicit_and_do_not_ship_as_app_assets(self):
        for path in (ROOT / "tools/grading/fixtures").glob("*.jsonl"):
            records = [json.loads(line) for line in path.read_text().splitlines()]
            self.assertTrue(all(r.get("synthetic") is True for r in records))
            self.assertTrue(all(r["chunkId"].startswith("SYNTHETIC-ONLY-") for r in records))
        for build in (ROOT / "app/build.gradle.kts", ROOT / "desktop/build.gradle.kts"):
            self.assertNotIn('from(rootProject.file("tools/grading/fixtures"))', build.read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
