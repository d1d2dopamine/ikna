package dev.ikna.domain.optimizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import dev.ikna.data.db.CardEntity
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.SettingsBackup
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Mock ACCEPTED verdicts test application wiring, not empirical estimator quality. */
class LocalOptimizerTest {
    private val start = 1_700_000_000_000L
    private val fitted = FsrsParams(FsrsParams.DEFAULT_W.mapIndexed { i,w -> if(i==8) w*0.8 else w })
    private fun rows(n: Int = 700) = (0 until n).map { i ->
        ReviewRecord(chunkId="card-${i%10}", ts=start+i*DAY_MS, rating=if(i%11==0) 1 else 3).toEntity(id=i+1L)
    }
    private fun accepted(p: FsrsParams = fitted) = Optimisation(p,600,0.4,0.35,Verdict.ACCEPTED)
    private class MemoryPreferences : DataStore<Preferences> {
        private val values = MutableStateFlow<Preferences>(emptyPreferences())
        private val lock = Mutex()
        var failWrites = false
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences)->Preferences): Preferences = lock.withLock {
            check(!failWrites); transform(values.value).also { values.value=it }
        }
    }
    private inner class Fixture(val memory: MemoryPreferences = MemoryPreferences(),
        val fitting: (List<ReviewSample>,Double,()->Unit)->Optimisation = {_,_,_->accepted()}) {
        val settings=SettingsStore(memory)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        @Volatile var now=start+900*DAY_MS
        @Volatile var history=rows()
        val calls=AtomicInteger()
        val controller=LocalOptimizer(settings,FsrsParams(),scope,{history},{now}) { samples,retention,cancel ->
            calls.incrementAndGet(); fitting(samples,retention,cancel)
        }
        suspend fun init()=controller.initialize()
        suspend fun run(){ controller.start()!!.join() }
        suspend fun await(p:(OptimizerUiState)->Boolean)=withTimeout(10000){controller.state.first(p)}
        fun close()=scope.cancel()
    }
    @Test fun `default startup never fits and accepted result requires explicit application`()=runBlocking {
        val f=Fixture();try {
            f.init();assertEquals(FsrsParams(),f.controller.parameters());assertEquals(0,f.calls.get())
            f.run();val s=f.await{it.stored.candidate!=null && !it.running}
            assertEquals(0.4,s.stored.latest!!.heldOutLossDefaults!!,0.0)
            assertEquals(0.35,s.stored.latest!!.heldOutLossOptimised!!,0.0)
            assertFalse(s.usingOptimized);assertNull(s.stored.applied)
            f.controller.applyCandidate();assertEquals(fitted,f.controller.parameters())
            f.controller.setEnabled(false);assertEquals(FsrsParams(),f.controller.parameters())
            f.controller.setEnabled(true);assertEquals(fitted,f.controller.parameters())
        }finally{f.close()}
    }
    @Test fun `restart reloads applied weights without fitting or replacing retention policy`()=runBlocking {
        val f=Fixture(fitting={_,_,_->accepted(fitted.copy(desiredRetention=0.77))})
        try {
            f.init();f.run();f.await{it.stored.candidate!=null};f.controller.applyCandidate()
            assertEquals(0.9,f.controller.parameters().desiredRetention,0.0);f.close()
            val other=Fixture(memory=f.memory);try {
                other.init();assertEquals(fitted,other.controller.parameters());assertEquals(0,other.calls.get())
                assertTrue(other.controller.state.value.usingOptimized)
            }finally{other.close()}
        }finally{f.close()}
    }
    @Test fun `cooldown and rejected refit preserve the applied result`()=runBlocking {
        var result=accepted();val f=Fixture(fitting={_,_,_->result})
        try {
            f.init();f.run();f.await{it.stored.candidate!=null};f.controller.applyCandidate()
            assertNull(f.controller.start());assertEquals(1,f.calls.get())
            f.now+=OPTIMIZER_REFIT_MS+1;result=Optimisation(null,600,0.4,0.41,Verdict.NO_IMPROVEMENT)
            f.run();f.await{it.stored.latest?.verdict==Verdict.NO_IMPROVEMENT && !it.running}
            assertEquals(fitted,f.controller.parameters());assertEquals(2,f.calls.get())
        }finally{f.close()}
    }
    @Test fun `new accepted candidate does not silently replace the active model`()=runBlocking {
        val newer=fitted.copy(w=fitted.w.mapIndexed{i,w->if(i==8) w*0.8 else w})
        var result=accepted();val f=Fixture(fitting={_,_,_->result})
        try {
            f.init();f.run();f.await{it.stored.candidate!=null};f.controller.applyCandidate()
            f.now+=OPTIMIZER_REFIT_MS+1;result=accepted(newer)
            f.run();f.await{it.stored.candidate?.parameters?.weights==newer.w}
            assertEquals(fitted,f.controller.parameters());f.controller.applyCandidate()
            assertEquals(newer,f.controller.parameters())
        }finally{f.close()}
    }
    @Test fun `too few answers have no NaN in storage and do not block eligibility checks`()=runBlocking {
        val f=Fixture(fitting={_,_,_->Optimisation(null,12,Double.NaN,Double.NaN,Verdict.TOO_FEW_ANSWERS)})
        try {
            f.init();f.run();f.await{it.stored.latest!=null && !it.running}
            val raw=f.settings.optimizerRaw.first()!!;assertFalse(raw.contains("NaN"))
            assertNull(OptimizerStateCodec.decode(raw)!!.latest!!.parameters)
            f.run();assertEquals(2,f.calls.get());assertEquals(FsrsParams(),f.controller.parameters())
        }finally{f.close()}
    }
    @Test fun `corrupt data is rejected before activation`()=runBlocking {
        val f=Fixture();try {
            f.memory.edit{it[stringPreferencesKey("localFsrsOptimizerV1")]="{broken"}
            f.init();assertEquals(OptimizerIssue.STORAGE,f.controller.state.value.issue)
            assertEquals(FsrsParams(),f.controller.parameters())
            assertNull(OptimizerStateCodec.decode("{\"formatVersion\":99}"))
            assertNull(OptimizerStateCodec.decode("{\"enabled\":true}"))
        }finally{f.close()}
    }
    @Test fun `cancel coalesces duplicate requests and reset cannot resurrect a running fit`()=runBlocking {
        for(reset in listOf(false,true)) {
            val entered=CompletableDeferred<Unit>();val gate=CountDownLatch(1)
            val f=Fixture(fitting={_,_,cancel->
                entered.complete(Unit);while(gate.count!=0L){cancel();Thread.sleep(1)};accepted()
            })
            try {
                f.init();val job=f.controller.start()!!;withTimeout(10000){entered.await()}
                assertNull(f.controller.start())
                if(reset)f.settings.clearAll() else f.controller.cancel()
                withTimeout(10000){job.join()}
                assertNull(f.settings.optimizerRaw.first());assertEquals(FsrsParams(),f.controller.parameters())
                assertEquals(1,f.calls.get())
            }finally{gate.countDown();f.close()}
        }
    }
    @Test fun `storage failure does not prevent immediate runtime defaults`()=runBlocking {
        val f=Fixture();try {
            f.init();f.run();f.await{it.stored.candidate!=null};f.controller.applyCandidate()
            f.memory.failWrites=true;f.controller.setEnabled(false)
            assertEquals(FsrsParams(),f.controller.parameters());assertFalse(f.controller.state.value.usingOptimized)
            assertEquals(OptimizerIssue.STORAGE,f.controller.state.value.issue)
        }finally{f.memory.failWrites=false;f.close()}
    }
    @Test fun `settings backup cannot activate local fitting`()=runBlocking {
        val f=Fixture();try {
            f.init();f.run();f.await{it.stored.candidate!=null};f.controller.applyCandidate()
            val backup=SettingsBackup.decode(SettingsBackup.encode(f.settings.current()))!!
            SettingsBackup.apply(f.settings,backup)
            assertEquals(FsrsParams(),f.controller.parameters())
            assertFalse(OptimizerStateCodec.decode(f.settings.optimizerRaw.first())!!.enabled)
        }finally{f.close()}
    }
    @Test fun `changed history and computation errors cannot publish an invalid candidate`()=runBlocking {
        lateinit var f:Fixture
        f=Fixture(fitting={_,_,_->f.history=f.history.drop(1);accepted()})
        try{f.init();f.run();assertEquals(OptimizerIssue.STALE_HISTORY,f.controller.state.value.issue);assertNull(f.settings.optimizerRaw.first())}finally{f.close()}
        val broken=Fixture(fitting={_,_,_->error("test failure")})
        try{broken.init();broken.run();assertEquals(OptimizerIssue.FAILED,broken.controller.state.value.issue);assertNull(broken.settings.optimizerRaw.first())}finally{broken.close()}
    }
    @Test fun `sample adapter filters retractions future suppression and duplicates not keyboard`() {
        val original=rows(5);val undo=original[1].copy(id=90,rating=0,undoOf=original[1].id)
        val result=optimizerInput(original+undo+original,start+3*DAY_MS,setOf("card-2"))
        assertEquals(listOf("card-0:0","card-3:0"),result.samples.map{it.cardKey})
        assertEquals(result,optimizerInput(original.reversed()+undo,start+3*DAY_MS,setOf("card-2")))
        assertEquals(64,result.fingerprint.length)
    }
    @Test fun `parameter and export round trips reject unsafe snapshots`() {
        assertEquals(fitted,FsrsSnapshotCodec.decode(FsrsSnapshotCodec.encode(fitted)))
        assertFalse(FsrsSnapshot.of(fitted).copy(formatVersion=99).isValid())
        assertFalse(FsrsSnapshot.of(fitted).copy(weights=listOf(1.0)).isValid())
        assertFalse(FsrsSnapshot.of(fitted).copy(desiredRetention=1.0).isValid())
        assertFalse(FsrsSnapshot.of(fitted).copy(weights=fitted.w.mapIndexed{i,w->if(i==0)Double.NaN else w}).isValid())
        val record=ReviewRecord(chunkId="one",ts=start,rating=3,fsrsParameters=FsrsSnapshotCodec.encode(fitted))
        val json=ReviewRecord.json.encodeToString(ReviewRecord.serializer(),record)
        val decoded=ReviewRecord.json.decodeFromString(ReviewRecord.serializer(),json)
        assertEquals(record,decoded);assertEquals(record.fsrsParameters,ReviewRecord.of(decoded.toEntity()).fsrsParameters)
    }
    @Test fun `dynamic scheduler reads only one model per answer and legacy replay uses defaults`() {
        val before=CardEntity("one",0,10.0,5.0,start,start-3*DAY_MS,start-10*DAY_MS)
        val calls=AtomicInteger();val dynamic=Scheduler(paramsProvider={calls.incrementAndGet();fitted})
        assertEquals(Scheduler(fitted).applyDerivedV1(before,Rating.EASY,start),dynamic.applyDerivedV1(before,Rating.EASY,start))
        assertEquals(1,calls.get())
        val old=ReviewRecord(chunkId="one",ts=start,rating=3).toEntity()
        assertEquals(Scheduler().apply(before,Rating.GOOD,start),dynamic.applyRecordedReview(before,old))
    }
    @Test fun `mixed default fitted and disabled history replays independently of current model`() {
        var live=CardEntity("one",0,1.0,5.0,start,null,start,isNew=true);var replay=live
        repeat(80){i->
            val engine=Scheduler(if(i in 20..49)fitted else FsrsParams(),dayStartHour=4)
            val input=if(i%13==0)Rating.AGAIN else Rating.GOOD
            val grade=if(input==Rating.AGAIN)input else when(i%4){1->Rating.HARD;2->Rating.EASY;else->input}
            val version=if(grade!=input)1 else null;val now=start+i*3*DAY_MS
            val result=if(version==1)engine.applyDerivedV1(live,grade,now) else engine.apply(live,input,now)
            val record=ReviewRecord(chunkId="one",ts=now,rating=grade.value,inputRating=input.value,gradingVersion=version,
                fsrsParameters=FsrsSnapshotCodec.encode(engine.currentParameters()))
            val json=ReviewRecord.json.encodeToString(ReviewRecord.serializer(),record)
            val restored=ReviewRecord.json.decodeFromString(ReviewRecord.serializer(),json).toEntity()
            replay=Scheduler(dayStartHour=4,paramsProvider={fitted}).applyRecordedReview(replay,restored).card
            live=result.card;assertEquals("answer $i",live,replay)
        }
    }
    @Test fun `estimator replay agrees with the production bounded memory transition`() {
        for(params in listOf(FsrsParams(),fitted))for(fresh in listOf(true,false))for(grade in Rating.entries){
            val before=CardEntity("one",0,10.0,5.0,start,if(fresh)null else start-3*DAY_MS,start-10*DAY_MS,isNew=fresh)
            val version=if(grade in listOf(Rating.HARD,Rating.EASY))1 else null
            val sample=ReviewSample("one:0",start,grade,if(version==1)Rating.GOOD else grade,version)
            val engine=Scheduler(params)
            val expected=if(version==1)engine.applyDerivedV1(before,grade,start).after else engine.apply(before,grade,start).after
            assertEquals(expected,FsrsOptimizer.replayState(if(fresh)null else MemoryState(10.0,5.0),if(fresh)0.0 else 3.0,sample,params))
        }
    }
    @Test fun `future outcomes cannot influence training loss`() {
        val a=ReviewSample("one:0",start,Rating.GOOD);val b=ReviewSample("one:0",start+2*DAY_MS,Rating.GOOD)
        val c=ReviewSample("one:0",start+4*DAY_MS,Rating.AGAIN);val cut=start+3*DAY_MS
        assertEquals(FsrsOptimizer.logLoss(fitted,listOf(listOf(a,b,c)),null,cut),
            FsrsOptimizer.logLoss(fitted,listOf(listOf(a,b,c.copy(rating=Rating.EASY))),null,cut))
    }
    @Test fun automaticPolicyAppliesOnlyAcceptedResultsAndHonoursMonthlyLimit() = runBlocking {
        val f = Fixture()
        try {
            f.init(); f.controller.runAutomaticCycle()
            assertEquals(fitted, f.controller.parameters())
            assertTrue(f.controller.state.value.usingOptimized)
            f.controller.runAutomaticCycle(); assertEquals(1, f.calls.get())
            f.now += OPTIMIZER_REFIT_MS + 1
            f.controller.runAutomaticCycle(); assertEquals(2, f.calls.get())
        } finally { f.close() }
    }
    @Test fun automaticEligibilityIsQuietAndInsufficientHistoryKeepsDefaults() = runBlocking {
        val f = Fixture(fitting = { _, _, _ -> Optimisation(null, 12, Double.NaN, Double.NaN, Verdict.TOO_FEW_ANSWERS) })
        try {
            f.init(); f.controller.runAutomaticCycle(); f.controller.runAutomaticCycle()
            assertEquals(1, f.calls.get()); assertEquals(FsrsParams(), f.controller.parameters())
            f.now += AutomaticLearningPolicy.ELIGIBILITY_RECHECK_MS + 1
            f.controller.runAutomaticCycle(); assertEquals(2, f.calls.get())
        } finally { f.close() }
    }
    @Test fun automaticRefusalNeverReplacesTheCurrentModel() = runBlocking {
        val f = Fixture(fitting = { _, _, _ -> Optimisation(null, 600, 0.4, 0.41, Verdict.NO_IMPROVEMENT) })
        try {
            f.init(); f.controller.runAutomaticCycle()
            assertEquals(FsrsParams(), f.controller.parameters())
            assertFalse(f.controller.state.value.usingOptimized)
        } finally { f.close() }
    }

    @Test fun automaticFailuresBackOffInsteadOfFittingAfterEveryAnswer() = runBlocking {
        val f = Fixture(fitting = { _, _, _ -> error("simulated local fitting failure") })
        try {
            f.init(); f.controller.runAutomaticCycle(); f.controller.runAutomaticCycle()
            assertEquals(1, f.calls.get()); assertEquals(FsrsParams(), f.controller.parameters())
            f.now += 3_600_001L
            f.controller.runAutomaticCycle(); assertEquals(2, f.calls.get())
        } finally { f.close() }
    }
    @Test fun automaticActivationCannotReviveAProfileAfterReset() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val f = Fixture(fitting = { _, _, check ->
            entered.countDown()
            kotlin.check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            check(); accepted()
        })
        try {
            f.init()
            val cycle = async(Dispatchers.Default) { f.controller.runAutomaticCycle() }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            f.controller.restoreDefaults(); release.countDown(); cycle.await()
            assertEquals(FsrsParams(), f.controller.parameters())
            assertFalse(f.controller.state.value.usingOptimized)
        } finally { release.countDown(); f.close() }
    }
    @Test fun automaticObserverIsIdempotentAndOwnedByApplicationScope() = runBlocking {
        val f = Fixture()
        try {
            f.init()
            val first = f.controller.startAutomatic(emptyFlow())
            val second = f.controller.startAutomatic(emptyFlow())
            assertSame(first, second); assertEquals(0, f.calls.get())
        } finally { f.close() }
    }

}
