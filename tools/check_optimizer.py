#!/usr/bin/env python3
"""Structural and real-SQLite checks, not a substitute for Kotlin/device CI."""
from pathlib import Path
import re,unittest
from check_grading import schema,identity_hash,create_database,seed_old_reviews
ROOT=Path(__file__).resolve().parents[1]
S=ROOT/'shared/src/jvmShared/kotlin/dev/ikna'
def migration():
 text=(S/'data/db/Migrations.kt').read_text().split('private val MIGRATION_7_8 =',1)[1].split('val ALL:',1)[0]
 return re.findall(r'connection\.execSQL\("([^"]+)"\)',text)
class OptimizerChecks(unittest.TestCase):
 def test_schema_is_one_nullable_addition(self):
  old,new=schema(7),schema(8)
  self.assertEqual(identity_hash(new),new['identityHash'])
  self.assertEqual(['ALTER TABLE reviews ADD COLUMN fsrsParameters TEXT'],migration())
  for a,b in zip(old['entities'],new['entities'],strict=True):
   if a['tableName']!='reviews':self.assertEqual(a,b)
   else:
    self.assertEqual(a['fields'],b['fields'][:-1]);self.assertEqual(a['indices'],b['indices'])
    self.assertEqual(dict(fieldPath='fsrsParameters',columnName='fsrsParameters',affinity='TEXT',notNull=False),b['fields'][-1])
 def test_migration_preserves_every_old_value(self):
  db=create_database(7);fresh=create_database(8);seed_old_reviews(db)
  db.execute("UPDATE reviews SET inputRating=3,gradingVersion=1,gradingReason='slow' WHERE id=41")
  cols=','.join('"'+r[1]+'"' for r in db.execute('PRAGMA table_info(reviews)'))
  before=db.execute(f'SELECT {cols} FROM reviews ORDER BY id').fetchall()
  for sql in migration():db.execute(sql)
  self.assertEqual(before,db.execute(f'SELECT {cols} FROM reviews ORDER BY id').fetchall())
  self.assertEqual([(None,),(None,)],db.execute('SELECT fsrsParameters FROM reviews').fetchall())
  for e in schema(8)['entities']:
   for p in ('table_info','index_list','foreign_key_list'):
    query=f'PRAGMA {p}("{e["tableName"]}")'
    self.assertEqual(fresh.execute(query).fetchall(),db.execute(query).fetchall())
  self.assertEqual(('ok',),db.execute('PRAGMA integrity_check').fetchone());db.close();fresh.close()
 def test_query_keeps_keyboard_not_undo_or_future(self):
  text=(S/'data/db/Daos.kt').read_text();end=text.index('suspend fun optimizerHistory');begin=text.rfind('@Query(',0,end)
  parts=re.findall(r'"([^"\n]*)"',text[begin:end])
  sql=parts[0]+'rating > 0 AND id NOT IN (SELECT undoOf FROM reviews WHERE undoOf IS NOT NULL)'+''.join(parts[1:])
  db=create_database(8);seed_old_reviews(db)
  for i in (100,101,102):
   db.execute("INSERT INTO reviews(id,chunkId,level,ts,rating,elapsedDays,stabilityBefore,stabilityAfter,difficultyBefore,difficultyAfter,durationMs,wasAmnesty,inputMethod) VALUES (?,'one',0,?,3,0,1,2,5,5,2000,0,'keyboard')",(i,i))
  self.assertEqual([101,100],[r[0] for r in db.execute(sql,dict(beforeTs=101,limit=20000))]);db.close()
 def test_answer_snapshot_precedes_writes(self):
  text=(S/'data/repo/LearningRepository.kt').read_text();text=text[text.index('val answerScheduler = scheduler.snapshot()'):]
  self.assertLess(text.index('FsrsSnapshotCodec.encode'),text.index('cardDao.upsert'))
  self.assertIn('fsrsParameters = parameterSnapshot',text);self.assertIn('answerScheduler.apply(before, rating, now)',text)
 def test_restore_validates_and_replays_each_model(self):
  text=(S/'data/repo/RestoreRepository.kt').read_text();self.assertLess(text.index('rec.fsrsParameters?.let'),text.index('insertRecords(answers'))
  replay=(S/'data/repo/GradingReplay.kt').read_text()
  for t in ('FsrsSnapshotCodec.decode(it)','defaultSnapshot()','engine.applyDerivedV1'):self.assertIn(t,replay)
  text=(S/'data/export/ReviewRecord.kt').read_text()
  for t in ('fsrsParameters = fsrsParameters','fsrsParameters = r.fsrsParameters'):self.assertIn(t,text)
 def test_background_worker_does_not_activate_or_reschedule(self):
  text=(S/'data/repo/LocalOptimizer.kt').read_text()
  for t in ('withContext(Dispatchers.Default)','currentCoroutineContext().ensureActive()','source.fingerprint != input().fingerprint','synchronized(runtimeLock)','desiredRetention = defaults.desiredRetention'):self.assertIn(t,text)
  for t in ('cardDao','HttpClient','java.net','scheduler.apply'):self.assertNotIn(t,text)
  body=text.split('private suspend fun runFit',1)[1].split('private fun activate',1)[0]
  self.assertNotIn('active.set',body);self.assertNotIn('enabled = true',body)
 def test_both_platforms_use_automatic_policy_and_provider(self):
  for p in ('app/src/main/java/dev/ikna/AppContainer.kt','desktop/src/main/kotlin/dev/ikna/desktop/DesktopContainer.kt'):
   text=(ROOT/p).read_text()
   for t in ('paramsProvider = optimizer::parameters','optimizer.initialize()','optimizerHistory','startAutomatic','observeOptimizerChanges','AutomaticLearningPolicy.DERIVED_WHEN_READY'):self.assertIn(t,text)
  for p in ('app/src/main/java/dev/ikna/ui/settings/SettingsScreen.kt','desktop/src/main/kotlin/dev/ikna/desktop/SettingsPane.kt'):
   self.assertNotIn('LocalOptimizerPanel',(ROOT/p).read_text())
   self.assertNotIn('grading.001',(ROOT/p).read_text())
  self.assertIn('store.disableLocalOptimizer()',(S/'data/export/SettingsBackup.kt').read_text())
 def test_estimator_uses_shared_cap_and_original_outcome(self):
  text=(S/'domain/fsrs/FsrsOptimizer.kt').read_text()
  for t in ('boundDerivedMemoryV1','(sample.inputRating ?: sample.rating) == Rating.AGAIN','if (until != null && sample.ts >= until) break','const val MIN_SCORED_ANSWERS = 500','const val MAX_ANSWERS = 20_000'):self.assertIn(t,text)
  self.assertIn('boundDerivedMemoryV1',(S/'domain/fsrs/Scheduler.kt').read_text())
 def test_version_and_ci_test_targets(self):
  text=(ROOT/'app/build.gradle.kts').read_text();self.assertIn('"0.10.0 press"',text);self.assertIn('200100000',text)
  for p in ('app/build.gradle.kts','shared/build.gradle.kts','desktop/build.gradle.kts'):self.assertIn('"2.2.20"',(ROOT/p).read_text())
  text=(ROOT/'desktop/build.gradle.kts').read_text()
  for name in ('optimizer','fsrs'):self.assertIn('app/src/test/java/dev/ikna/domain/'+name,text)
if __name__=='__main__':unittest.main(verbosity=2)
