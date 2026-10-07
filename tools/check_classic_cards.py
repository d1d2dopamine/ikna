#!/usr/bin/env python3
"""Execute actual DAO SQL against schema 10; not a Kotlin/Room compilation."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared/src/jvmShared/kotlin/dev/ikna"
SOURCE = (SHARED / "data/db/Daos.kt").read_text()
REVIEW_SOURCE = SOURCE.split("@Dao\ninterface ReviewDao", 1)[1].split("@Dao\ninterface StatsDao", 1)[0]
NOT_RETRACTED_LITERAL = re.search(r'NOT_RETRACTED\s*=\s*("(?:\\.|[^"\\])*")', SOURCE).group(1)
REVIEW_QUERIES = {
    name: "".join(json.loads(literal) for literal in re.findall(r'"(?:\\.|[^"\\])*"', body.replace("NOT_RETRACTED", NOT_RETRACTED_LITERAL)))
    for body, name in re.findall(r'@Query\((.*?)\)\s*(?:suspend\s+)?fun\s+(\w+)', REVIEW_SOURCE, re.S)
}
# DAO method names repeat in other interfaces; use only ChunkDao/CardDao here.
SOURCE = SOURCE.split("@Dao\ninterface ReviewDao", 1)[0]
QUERIES = {
    name: "".join(json.loads(literal) for literal in re.findall(r'"(?:\\.|[^"\\])*"', body))
    for body, name in re.findall(r'@Query\((.*?)\)\s*(?:suspend\s+)?fun\s+(\w+)', SOURCE, re.S)
}


class ClassicDaoTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        schema = json.loads((ROOT / "app/schemas/dev.ikna.data.db.IknaDatabase/10.json").read_text())["database"]
        for entity in schema["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        self.db.execute("INSERT INTO packs (id,version,lang,chunkCount,installedAt,title,isActive) VALUES ('p',1,'en',3,0,'Deck',1)")
        # Every classic row has retired siblings with more urgent/larger values.
        for number, due, amnesty in [(0, 10, 0), (1, 90, 0), (2, 1, 1)]:
            target = f"c{number}"
            self.db.execute("INSERT INTO chunks (id,packId,lang,text,contextSentence,translation,targetStart,targetEnd,freqRank) "
                            "VALUES (?, 'p', 'en', 'word', 'full sentence', 'meaning', 0, 4, ?)", (target, number))
            self.db.execute("INSERT INTO pack_chunks (packId,chunkId,freqRank) VALUES ('p',?,?)", (target, number))
            for level in (0, 1, 2):
                self.db.execute("INSERT INTO cards (chunkId,level,stability,difficulty,dueAt,lastReviewAt,introducedAt,reps,lapses,inAmnesty,isNew) "
                                "VALUES (?, ?, 5, 5, ?, 0, 0, 5, ?, ?, 0)",
                                (target, level, due if level == 0 else -100, 5 if level == 0 else 50, amnesty))
        self.db.commit()

    def query(self, name, **parameters):
        return self.db.execute(QUERIES[name], parameters)

    def levels(self, name, **parameters):
        cursor = self.query(name, **parameters)
        level = [column[0] for column in cursor.description].index("level")
        return [row[level] for row in cursor.fetchall()]

    def test_due_backlog_forecast_and_next_due_ignore_retired_modes(self):
        self.assertEqual(1, self.query("dueCount", until=20).fetchone()[0])
        self.assertEqual(1, self.query("amnestyCount").fetchone()[0])
        self.assertEqual(1, self.query("dueBetween", **{"from": 20, "to": 100}).fetchone()[0])
        self.assertEqual(90, self.query("nextDueAt", after=20).fetchone()[0])
        self.assertEqual(3, self.query("cardCountFlow").fetchone()[0])
        self.assertEqual(6, self.query("retiredCount").fetchone()[0])

    def test_queue_and_leeches_only_return_classic_rows(self):
        cases = {
            "dueCards": dict(until=20, limit=10),
            "amnestyCards": dict(limit=10),
            "leeches": dict(minLapses=3, limit=10),
            "dueCardsExcluding": dict(until=20, exclude="", limit=10),
            "amnestyCardsExcluding": dict(exclude="", limit=10),
            "upcomingCardsExcluding": dict(after=20, exclude="", limit=10),
            "dueCardsForPackExcluding": dict(packId="p", until=20, exclude="", limit=10),
            "amnestyCardsForPackExcluding": dict(packId="p", exclude="", limit=10),
            "upcomingCardsForPackExcluding": dict(packId="p", after=20, exclude="", limit=10),
            "browseCandidatesForPack": dict(packId="p", after=20, limit=10),
        }
        for name, parameters in cases.items():
            with self.subTest(query=name):
                levels = self.levels(name, **parameters)
                self.assertTrue(levels)
                self.assertEqual({0}, set(levels))

    def test_amnesty_does_not_rewrite_archived_schedules(self):
        before = self.db.execute("SELECT * FROM cards WHERE level != 0 ORDER BY chunkId,level").fetchall()
        self.assertEqual(1, self.query("moveOverdueToAmnesty", threshold=20).rowcount)
        after = self.db.execute("SELECT * FROM cards WHERE level != 0 ORDER BY chunkId,level").fetchall()
        self.assertEqual(before, after)
        self.assertEqual(2, self.query("amnestyCount").fetchone()[0])

    def test_archive_reads_still_preserve_all_levels(self):
        self.assertEqual([0, 1, 2] * 3, self.levels("all"))
        self.assertEqual([2], self.levels("card", chunkId="c0", level=2))
        self.assertEqual([1], self.levels("byKeys", keys="c0:1"))
        self.db.execute("DELETE FROM cards WHERE level=0")
        self.assertFalse(self.query("hasAny").fetchone()[0])
        self.assertEqual(3, self.query("untouchedCount").fetchone()[0])
        self.assertEqual(3, self.query("untouchedCountFor", packId="p").fetchone()[0])
        self.assertEqual(3, len(self.query("unintroducedByFrequency", limit=10).fetchall()))
        self.assertEqual(3, len(self.query("unintroducedByFrequencyFor", packId="p", limit=10).fetchall()))

    def test_undo_restores_valid_answer_count_while_journal_grows(self):
        # Execute the actual DAO count expressions, not a renamed copy of total().
        insert = ("INSERT INTO reviews (id,chunkId,level,ts,rating,elapsedDays,"
                  "stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,"
                  "durationMs,wasAmnesty,undoOf) VALUES (?, 'c0', 0, ?, ?, 1, 5, 6, 5, 5, 4200, 0, ?)")
        self.db.execute(insert, (1, 1, 3, None))
        kept = self.db.execute("SELECT * FROM reviews WHERE id=1").fetchone()
        valid = lambda: self.db.execute(REVIEW_QUERIES["total"]).fetchone()[0]
        raw = lambda: self.db.execute(REVIEW_QUERIES["observeOptimizerChanges"]).fetchone()[0]
        self.assertEqual((1, 1), (valid(), raw()))
        self.db.execute(insert, (2, 2, 3, None))
        self.assertEqual((2, 2), (valid(), raw()))
        self.db.execute(insert, (3, 3, 0, 2))
        self.assertEqual((1, 3), (valid(), raw()))
        self.assertEqual(kept, self.db.execute("SELECT * FROM reviews WHERE id=1").fetchone())
        self.assertEqual(3, len(self.db.execute("SELECT * FROM reviews").fetchall()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
