#!/usr/bin/env python3
"""Offline structural/SQLite checks, NOT a Kotlin build or Android test.

The identity calculation follows Room's published SchemaIdentityKey, Entity,
Property, PrimaryKey, Index and Database implementations:
https://android.googlesource.com/platform/frameworks/support/+/androidx-room-release/room/room-compiler/src/main/kotlin/androidx/room/vo/

The calculation is checked against all three pre-existing exported schemas.
It only supports the schema features used here (no views or foreign keys).
KSP output and on-device validation remain the release gate.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared/src/jvmShared/kotlin/dev/ikna"
SCHEMAS = ROOT / "app/schemas/dev.ikna.data.db.IknaDatabase"
COLUMNS = {
    "latencyMs": ("Long", "INTEGER"),
    "swipeVelocityX": ("Float", "REAL"),
    "peeked": ("Boolean", "INTEGER"),
    "timingDiscardReason": ("String", "TEXT"),
}


def schema(version):
    return json.loads((SCHEMAS / f"{version}.json").read_text())["database"]


def identity_hash(db):
    def flag(v):
        return str(v).lower()

    def digest(items):
        return hashlib.md5("".join(x + "?:?" for x in items).encode()).hexdigest()

    identities = []
    assert not db.get("views")
    for entity in db["entities"]:
        assert not entity["foreignKeys"]
        pk = entity["primaryKey"]
        items = [entity["tableName"], flag(pk["autoGenerate"]) + "-[" + ", ".join(pk["columnNames"]) + "]"]
        fields = [
            f["columnName"] + "-" + f["affinity"] + "-" + flag(f["notNull"])
            + ("-defaultValue=" + f["defaultValue"] if f.get("defaultValue") is not None else "")
            for f in entity["fields"]
        ]
        indices = [
            flag(i["unique"]) + "-" + i["name"] + "-" + ",".join(i["columnNames"])
            + ("-" + ",".join(i["orders"]) if i["orders"] else "")
            for i in entity["indices"]
        ]
        items += sorted(fields, key=str.lower) + sorted(indices, key=str.lower)
        identities.append(digest(items))
    return digest(sorted(identities, key=str.lower))


def migration_sql():
    source = (SHARED / "data/db/Migrations.kt").read_text()
    body = source.split("private val MIGRATION_5_6 =", 1)[1].split("private val MIGRATION_6_7", 1)[0].split("val ALL:", 1)[0]
    return re.findall(r'connection\.execSQL\("([^"]+)"\)', body)


def create_database(version):
    db = sqlite3.connect(":memory:")
    for entity in schema(version)["entities"]:
        name = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity["indices"]:
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))
    return db


def seed_old_reviews(db):
    db.execute("""INSERT INTO reviews (id,chunkId,level,ts,rating,elapsedDays,
        stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,
        wasAmnesty,prevStability,prevDifficulty,prevDueAt,prevLastReviewAt,prevReps,
        prevLapses,prevIsNew,prevInAmnesty)
        VALUES (41,'kept',1,1700000000000,3,1.5,2,4,5,5.1,4200,0,2,5,1800000000000,
        1699990000000,7,2,0,1)""")
    db.execute("""INSERT INTO reviews (id,chunkId,level,ts,rating,elapsedDays,
        stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,undoOf)
        VALUES (42,'kept',1,1700000000001,0,0,0,0,0,0,0,0,41)""")


class GradingChecks(unittest.TestCase):
    def test_room_identity_matches_all_schema_versions(self):
        for version in (3, 4, 5, 6):
            with self.subTest(version=version):
                db = schema(version)
                self.assertEqual(identity_hash(db), db["identityHash"])
                self.assertIn(db["identityHash"], db["setupQueries"][1])

    def test_migration_only_adds_the_four_nullable_columns(self):
        self.assertEqual(migration_sql(), [
            f"ALTER TABLE reviews ADD COLUMN {name} {sql_type}"
            for name, (_, sql_type) in COLUMNS.items()
        ])

    def test_migration_preserves_every_old_value_and_undo(self):
        db = create_database(5)
        seed_old_reviews(db)
        columns = [r[1] for r in db.execute("PRAGMA table_info(reviews)")]
        selected = ",".join('"' + c + '"' for c in columns)
        before = db.execute(f"SELECT {selected} FROM reviews ORDER BY id").fetchall()
        indices = db.execute("PRAGMA index_list(reviews)").fetchall()
        for sql in migration_sql():
            db.execute(sql)
        self.assertEqual(before, db.execute(f"SELECT {selected} FROM reviews ORDER BY id").fetchall())
        self.assertEqual(indices, db.execute("PRAGMA index_list(reviews)").fetchall())
        self.assertEqual([(None,) * 4] * 2, db.execute(
            "SELECT latencyMs,swipeVelocityX,peeked,timingDiscardReason FROM reviews ORDER BY id"
        ).fetchall())
        db.execute("""INSERT INTO reviews (chunkId,level,ts,rating,elapsedDays,stabilityBefore,
            stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,
            latencyMs,swipeVelocityX,peeked,timingDiscardReason)
            VALUES ('new',0,1700000000002,1,0,1,1,5,5,4000,0,900,-1250.5,1,'focus_lost')""")
        self.assertEqual((43, 900, -1250.5, 1, "focus_lost"), db.execute(
            "SELECT id,latencyMs,swipeVelocityX,peeked,timingDiscardReason FROM reviews WHERE chunkId='new'"
        ).fetchone())
        self.assertEqual(("ok",), db.execute("PRAGMA integrity_check").fetchone())
        db.close()

    def test_migrated_and_fresh_schemas_agree_for_every_table_and_index(self):
        migrated, fresh = create_database(5), create_database(6)
        for sql in migration_sql():
            migrated.execute(sql)
        for entity in schema(6)["entities"]:
            name = entity["tableName"]
            for pragma in ("table_info", "index_list", "foreign_key_list"):
                with self.subTest(table=name, pragma=pragma):
                    query = f'PRAGMA {pragma}("{name}")'
                    self.assertEqual(migrated.execute(query).fetchall(), fresh.execute(query).fetchall())
            for index in entity["indices"]:
                query = f'PRAGMA index_xinfo("{index["name"]}")'
                self.assertEqual(migrated.execute(query).fetchall(), fresh.execute(query).fetchall())
        migrated.close()
        fresh.close()

    def test_only_reviews_change_in_schema_six(self):
        old, new = schema(5), schema(6)
        for before, after in zip(old["entities"], new["entities"], strict=True):
            if before["tableName"] != "reviews":
                self.assertEqual(before, after)
                continue
            self.assertEqual(before["fields"], after["fields"][:-4])
            for (name, (_, affinity)), field in zip(COLUMNS.items(), after["fields"][-4:], strict=True):
                self.assertEqual(field, dict(fieldPath=name, columnName=name, affinity=affinity, notNull=False))
            for key in ("indices", "primaryKey", "foreignKeys"):
                self.assertEqual(before[key], after[key])

    def test_nullable_defaults_and_export_mappings_are_complete(self):
        entity = (SHARED / "data/db/Entities.kt").read_text()
        record = (SHARED / "data/export/ReviewRecord.kt").read_text()
        repository = (SHARED / "data/repo/LearningRepository.kt").read_text()
        for name, (kind, _) in COLUMNS.items():
            for source in (entity, record):
                self.assertIn(f"val {name}: {kind}? = null", source)
            self.assertIn(f"{name} = {name}", record)
            self.assertIn(f"{name} = r.{name}", record)
            source = "decision" if name == "timingDiscardReason" else "signals"
            self.assertIn(f"{name} = {source}.{name}", repository)

    def test_signal_math_never_enters_scheduler(self):
        for path in (SHARED / "domain/fsrs").glob("*.kt"):
            self.assertNotIn("ReviewSignals", path.read_text())
            self.assertNotIn("ReviewSignalTracker", path.read_text())
        self.assertIn("answerScheduler.apply(before, rating, now)", (SHARED / "data/repo/LearningRepository.kt").read_text())

    def test_observations_are_frozen_before_throw_animation(self):
        source = (SHARED / "ui/session/CardStack.kt").read_text()
        snapshot = source.index("val observation = signals.snapshot(velocity.x)")
        throw = source.index("if (animations) throwOut(offsetX, rating, velocity)")
        rate = source.index("rateNow.value(rating, observation)")
        self.assertLess(snapshot, throw)
        self.assertLess(throw, rate)
        self.assertIn(".onGloballyPositioned { signals.shown() }", source)
        self.assertIn("signals.dragStarted()", source)
        self.assertIn("customActions = if (revealed)", source)
        self.assertIn("signals.answerStarted(INPUT_ACCESSIBILITY)", source)
        self.assertIn("rateNow.value(Rating.GOOD, signals.snapshot())", source)
        self.assertIn("rateNow.value(Rating.AGAIN, signals.snapshot())", source)

    def test_android_interruption_observers_are_registered_and_removed(self):
        source = (ROOT / "app/src/main/java/dev/ikna/ui/session/ReviewInterruptions.kt").read_text()
        for event in ("ON_PAUSE", "ON_STOP", "ACTION_SCREEN_OFF", "OnWindowFocusChangeListener"):
            self.assertIn(event, source)
        for cleanup in ("removeObserver", "removeOnWindowFocusChangeListener", "unregisterReceiver"):
            self.assertIn(cleanup, source)

    def test_desktop_keyboard_uses_its_own_automatic_grading_path(self):
        source = (ROOT / "desktop/src/main/kotlin/dev/ikna/desktop/SessionPane.kt").read_text()
        card = (SHARED / "ui/session/CardStack.kt").read_text()
        grading = (SHARED / "domain/grading/DerivedGrading.kt").read_text()
        replay = (SHARED / "data/repo/GradingReplay.kt").read_text()
        dao = (SHARED / "data/db/Daos.kt").read_text()
        hotkeys = (SHARED / "data/prefs/Hotkeys.kt").read_text()
        for required in ["ProgrammaticSwipe", "requestKeyboardSwipe(Rating.AGAIN)",
                         "requestKeyboardSwipe(Rating.GOOD)", "reveal(INPUT_KEYBOARD)",
                         "HotkeyBindings.decode(settings.hotkeys)"]:
            self.assertIn(required, source)
        for forbidden in ["HotkeyAction.AGAIN", "HotkeyAction.HARD",
                          "HotkeyAction.GOOD", "HotkeyAction.EASY"]:
            self.assertNotIn(forbidden, source + hotkeys)
        self.assertIn("signals.keyboardStarted()", card)
        self.assertIn("signals.snapshot(inputMethod = INPUT_KEYBOARD)", card)
        self.assertIn("INPUT_KEYBOARD -> signals.swipeVelocityX == null", grading)
        self.assertIn("inputMethod: String = INPUT_SWIPE", replay)
        self.assertIn("inputMethod = :inputMethod", dao)
        self.assertIn("peekSemantics = :peekSemantics", dao)
        self.assertIn("inputRating = 3", dao)
        self.assertIn("peeked = 1", dao)
        self.assertIn("required_reveal_verified_v2", grading)
        self.assertIn("remember(deckId, reload, index, current?.card?.key, loading)", source)
        self.assertLess(source.index(".onPreviewKeyEvent { event ->"), source.index(".focusable()"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
