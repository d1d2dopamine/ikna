Run bash tools/ci/run-gradle.sh ci-logs/android-test-apk.log :app:assembleDebug :app:assembleDebugAndroidTest -Pikna.abi=emulator&#x20;

To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to [https://docs.gradle.org/8.10.2/userguide/gradle_daemon.html#sec:disabling_the_daemon](https://docs.gradle.org/8.10.2/userguide/gradle_daemon.html#sec:disabling_the_daemon) in the Gradle documentation.&#x20;

Daemon will be stopped at the end of the build &#x20;

\> Task :shared:preBuild UP-TO-DATE&#x20;

\> Task :app:preBuild UP-TO-DATE&#x20;

\> Task :app:preDebugBuild UP-TO-DATE&#x20;

\> Task :shared:preDebugBuild UP-TO-DATE&#x20;

\> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE&#x20;

\> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED&#x20;

\> Task :app:generateDebugResValues&#x20;

\> Task :shared:writeDebugAarMetadata&#x20;

\> Task :shared:generateDebugResValues FROM-CACHE&#x20;

\> Task :app:generateDebugResources&#x20;

\> Task :shared:generateDebugResources&#x20;

\> Task :shared:packageDebugResources&#x20;

\> Task :shared:extractDeepLinksDebug&#x20;

\> Task :shared:processDebugManifest&#x20;

\> Task :shared:compileDebugLibraryResources&#x20;

\> Task :app:checkDebugAarMetadata&#x20;

\> Task :app:mapDebugSourceSetPaths&#x20;

\> Task :shared:parseDebugLocalResources&#x20;

\> Task :shared:kmpPartiallyResolvedDependenciesChecker&#x20;

\> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED&#x20;

\> Task :shared:convertXmlValueResourcesForAndroidMain NO-SOURCE&#x20;

\> Task :shared:copyNonXmlValueResourcesForAndroidMain NO-SOURCE&#x20;

\> Task :shared:prepareComposeResourcesTaskForAndroidMain NO-SOURCE&#x20;

\> Task :shared:generateResourceAccessorsForAndroidMain SKIPPED&#x20;

\> Task :shared:convertXmlValueResourcesForCommonMain NO-SOURCE&#x20;

\> Task :shared:copyNonXmlValueResourcesForCommonMain NO-SOURCE&#x20;

\> Task :shared:prepareComposeResourcesTaskForCommonMain NO-SOURCE&#x20;

\> Task :shared:generateResourceAccessorsForCommonMain SKIPPED&#x20;

\> Task :shared:convertXmlValueResourcesForJvmShared NO-SOURCE&#x20;

\> Task :shared:copyNonXmlValueResourcesForJvmShared NO-SOURCE&#x20;

\> Task :shared:prepareComposeResourcesTaskForJvmShared NO-SOURCE&#x20;

\> Task :shared:generateResourceAccessorsForJvmShared SKIPPED&#x20;

\> Task :shared:generateActualResourceCollectorsForAndroidMain SKIPPED&#x20;

\> Task :shared:generateComposeResClass SKIPPED&#x20;

\> Task :shared:generateExpectResourceCollectorsForCommonMain SKIPPED&#x20;

\> Task :shared:convertXmlValueResourcesForAndroidDebug NO-SOURCE&#x20;

\> Task :shared:copyNonXmlValueResourcesForAndroidDebug NO-SOURCE&#x20;

\> Task :shared:prepareComposeResourcesTaskForAndroidDebug NO-SOURCE&#x20;

\> Task :shared:generateResourceAccessorsForAndroidDebug SKIPPED&#x20;

\> Task :shared:generateDebugRFile&#x20;

\> Task :shared:javaPreCompileDebug&#x20;

\> Task :app:packageDebugResources&#x20;

\> Task :app:createDebugCompatibleScreenManifests&#x20;

\> Task :app:extractDeepLinksDebug FROM-CACHE&#x20;

\> Task :app:parseDebugLocalResources&#x20;

\> Task :app:mergeDebugResources&#x20;

\> Task :app:processDebugMainManifest&#x20;

\> Task :shared:mergeDebugShaders&#x20;

\> Task :app:processDebugManifest&#x20;

\> Task :shared:compileDebugShaders NO-SOURCE&#x20;

\> Task :app:javaPreCompileDebug FROM-CACHE&#x20;

\> Task :shared:copyDebugComposeResourcesToAndroidAssets&#x20;

\> Task :shared:debugAssetsCopyForAGP NO-SOURCE&#x20;

\> Task :shared:generateDebugAssets UP-TO-DATE&#x20;

\> Task :app:mergeDebugShaders&#x20;

\> Task :shared:packageDebugAssets&#x20;

\> Task :app:compileDebugShaders NO-SOURCE&#x20;

\> Task :app:processDebugManifestForPackage&#x20;

\> Task :shared:mergeDebugJniLibFolders&#x20;

\> Task :shared:mergeDebugNativeLibs NO-SOURCE&#x20;

\> Task :shared:copyDebugJniLibsProjectOnly&#x20;

\> Task :app:generateDebugAssets UP-TO-DATE&#x20;

\> Task :app:mergeDebugAssets&#x20;

\> Task :app:compressDebugAssets&#x20;

\> Task :app:processDebugResources&#x20;

\> Task :app:mergeDebugJniLibFolders&#x20;

\> Task :app:checkDebugDuplicateClasses&#x20;

\> Task :app:validateSigningDebug&#x20;

\> Task :app:writeDebugAppMetadata&#x20;

\> Task :app:writeDebugSigningConfigVersions&#x20;

\> Task :app:preDebugAndroidTestBuild SKIPPED&#x20;

\> Task :app:checkDebugAndroidTestAarMetadata&#x20;

\> Task :app:generateDebugAndroidTestResValues FROM-CACHE&#x20;

\> Task :app:mapDebugAndroidTestSourceSetPaths&#x20;

\> Task :app:generateDebugAndroidTestResources&#x20;

\> Task :app:mergeDebugNativeLibs&#x20;

\> Task :app:mergeDebugAndroidTestResources&#x20;

\> Task :app:stripDebugDebugSymbols&#x20;

Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so, libonnxruntime.so, libsherpa-onnx-c-api.so, libsherpa-onnx-cxx-api.so, libsherpa-onnx-jni.so.&#x20;

\> Task :app:processDebugAndroidTestManifest&#x20;

\> Task :app:javaPreCompileDebugAndroidTest FROM-CACHE&#x20;

\> Task :app:mergeDebugAndroidTestShaders&#x20;

\> Task :app:compileDebugAndroidTestShaders NO-SOURCE&#x20;

\> Task :app:generateDebugAndroidTestAssets UP-TO-DATE&#x20;

\> Task :app:mergeDebugAndroidTestAssets&#x20;

\> Task :app:compressDebugAndroidTestAssets&#x20;

\> Task :app:desugarDebugAndroidTestFileDependencies&#x20;

\> Task :app:checkDebugAndroidTestDuplicateClasses&#x20;

\> Task :app:processDebugAndroidTestResources&#x20;

\> Task :app:desugarDebugFileDependencies&#x20;

\> Task :shared:kspDebugKotlinAndroid&#x20;

\> Task :app:mergeExtDexDebugAndroidTest&#x20;

\> Task :app:mergeDebugAndroidTestJniLibFolders&#x20;

\> Task :app:mergeDebugAndroidTestNativeLibs NO-SOURCE&#x20;

\> Task :app:stripDebugAndroidTestDebugSymbols NO-SOURCE&#x20;

\> Task :app:validateSigningDebugAndroidTest&#x20;

\> Task :app:writeDebugAndroidTestSigningConfigVersions&#x20;

\> Task :app:mergeExtDexDebug&#x20;

\> Task :shared:compileDebugKotlinAndroid&#x20;

w: file:///home/runner/work/ikna/ikna/shared/build/generated/ksp/android/androidDebug/kotlin/dev/ikna/data/db/IknaDatabaseConstructor.kt:5:8 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: [https://youtrack.jetbrains.com/issue/KT-61573](https://youtrack.jetbrains.com/issue/KT-61573)&#x20;

w: file:///home/runner/work/ikna/ikna/shared/src/jvmShared/kotlin/dev/ikna/data/db/IknaDatabase.kt:76:1 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: [https://youtrack.jetbrains.com/issue/KT-61573](https://youtrack.jetbrains.com/issue/KT-61573)&#x20;

w: file:///home/runner/work/ikna/ikna/shared/src/jvmShared/kotlin/dev/ikna/data/dev/DeveloperMode.kt:71:5 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.&#x20;

w: file:///home/runner/work/ikna/ikna/shared/src/jvmShared/kotlin/dev/ikna/data/dev/DeveloperMode.kt:72:37 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.&#x20;

w: file:///home/runner/work/ikna/ikna/shared/src/jvmShared/kotlin/dev/ikna/ui/dev/DeveloperToolsPanel.kt:31:21 'val LocalClipboardManager: ProvidableCompositionLocal\<ClipboardManager>' is deprecated. Use LocalClipboard instead which supports suspend functions.&#x20;

w: file:///home/runner/work/ikna/ikna/shared/src/jvmShared/kotlin/dev/ikna/ui/theme/SignalFrame.kt:179:33 The corresponding parameter in the supertype 'PointerInputModifierNode' is named 'pointerEvent'. This may cause problems when calling this function with named arguments.&#x20;

\> Task :shared:compileDebugJavaWithJavac NO-SOURCE&#x20;

\> Task :shared:processDebugJavaRes&#x20;

\> Task :shared:bundleLibCompileToJarDebug&#x20;

\> Task :shared:bundleLibRuntimeToJarDebug&#x20;

\> Task :shared:bundleLibRuntimeToDirDebug&#x20;

\> Task :app:compileDebugKotlin&#x20;

e: file:///home/runner/work/ikna/ikna/app/src/main/java/dev/ikna/data/export/JsonExporter.kt:87:9 Cannot access 'fun \<T : Any> writeExportDocument(create: () -> T?, open: (T) -> OutputStream?, body: (BufferedWriter) -> Unit): Unit': it is internal in file.&#x20;

\> Task :app:compileDebugKotlin FAILED&#x20;

gradle/actions: Writing build results to /home/runner/work/\_temp/.gradle-actions/build-results/\_\_run_3-1791821275064.json&#x20;

FAILURE: Build failed with an exception.&#x20;

\* What went wrong:&#x20;

Execution failed for task ':app:compileDebugKotlin'.&#x20;

\> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction&#x20;

&#x20;  \> Compilation error. See log for more details&#x20;

\* Try:&#x20;

\> Run with --info or --debug option to get more log output.&#x20;

\> Run with --scan to get full insights.&#x20;

\> Get more help at [https://help.gradle.org.](https://help.gradle.org./)&#x20;

\* Exception is:&#x20;

org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:130)&#x20;

&#x9;at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:293)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:128)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)&#x20;

&#x9;at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)&#x20;

&#x9;at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)&#x20;

&#x9;at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)&#x20;

&#x9;at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)&#x20;

&#x9;at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)&#x20;

&#x9;at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)&#x20;

Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction&#x20;

&#x9;at org.gradle.workers.internal.DefaultWorkerExecutor$WorkItemExecution.waitForCompletion(DefaultWorkerExecutor.java:287)&#x20;

&#x9;at org.gradle.internal.work.DefaultAsyncWorkTracker.lambda$waitForItemsAndGatherFailures$2(DefaultAsyncWorkTracker.java:130)&#x20;

&#x9;at org.gradle.internal.Factories$1.create(Factories.java:31)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:335)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:318)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLock(DefaultWorkerLeaseService.java:323)&#x20;

&#x9;at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:126)&#x20;

&#x9;at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:92)&#x20;

&#x9;at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForAll(DefaultAsyncWorkTracker.java:78)&#x20;

&#x9;at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForCompletion(DefaultAsyncWorkTracker.java:66)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:252)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:29)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:26)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:47)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:229)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:212)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:195)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:162)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:105)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:44)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:59)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:56)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:56)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:44)&#x20;

&#x9;at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:42)&#x20;

&#x9;at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:75)&#x20;

&#x9;at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)&#x20;

&#x9;at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:50)&#x20;

&#x9;at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:28)&#x20;

&#x9;at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:67)&#x20;

&#x9;at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:37)&#x20;

&#x9;at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:61)&#x20;

&#x9;at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:26)&#x20;

&#x9;at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:69)&#x20;

&#x9;at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:46)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:40)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:29)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.executeAndStoreInCache(BuildCacheStep.java:145)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.lambda$executeWithCache$4(BuildCacheStep.java:101)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.lambda$executeWithCache$5(BuildCacheStep.java:101)&#x20;

&#x9;at org.gradle.internal.Try$Success.map(Try.java:175)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.executeWithCache(BuildCacheStep.java:85)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$0(BuildCacheStep.java:74)&#x20;

&#x9;at org.gradle.internal.Either$Left.fold(Either.java:115)&#x20;

&#x9;at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)&#x20;

&#x9;at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)&#x20;

&#x9;at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:46)&#x20;

&#x9;at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:35)&#x20;

&#x9;at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)&#x20;

&#x9;at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)&#x20;

&#x9;at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)&#x20;

&#x9;at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)&#x20;

&#x9;at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)&#x20;

&#x9;at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:49)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:27)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:71)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:39)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)&#x20;

&#x9;at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)&#x20;

&#x9;at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:107)&#x20;

&#x9;at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:56)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:64)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:43)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:125)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:61)&#x20;

&#x9;at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:36)&#x20;

&#x9;at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)&#x20;

&#x9;at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)&#x20;

&#x9;at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)&#x20;

&#x9;at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:75)&#x20;

&#x9;at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:41)&#x20;

&#x9;at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:289)&#x20;

&#x9;at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)&#x20;

&#x9;at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)&#x20;

&#x9;at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)&#x20;

&#x9;at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)&#x20;

&#x9;at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)&#x20;

&#x9;at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)&#x20;

&#x9;at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)&#x20;

&#x9;at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:48)&#x20;

&#x9;at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:35)&#x20;

&#x9;at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:61)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:127)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)&#x20;

&#x9;at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)&#x20;

&#x9;at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)&#x20;

&#x9;at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)&#x20;

&#x9;at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)&#x20;

&#x9;at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)&#x20;

&#x9;at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)&#x20;

&#x9;at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)&#x20;

&#x9;at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)&#x20;

Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details&#x20;

&#x9;at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:21)&#x20;

&#x9;at org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.run(GradleKotlinCompilerWork.kt:119)&#x20;

&#x9;at org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction.execute(GradleCompilerRunnerWithWorkers.kt:74)&#x20;

&#x9;at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)&#x20;

&#x9;at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)&#x20;

&#x9;at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)&#x20;

&#x9;at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)&#x20;

&#x9;at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)&#x20;

&#x9;at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)&#x20;

&#x9;at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:209)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:166)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)&#x20;

&#x9;at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)&#x20;

&#x9;at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)&#x20;

&#x9;at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)&#x20;

&#x9;at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:174)&#x20;

&#x9;at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:194)&#x20;

&#x9;at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:127)&#x20;

&#x9;at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:169)&#x20;

&#x9;at org.gradle.internal.Factories$1.create(Factories.java:31)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)&#x20;

&#x9;at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:132)&#x20;

&#x9;at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:164)&#x20;

&#x9;at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:133)&#x20;

&#x9;... 2 more&#x20;

BUILD FAILED in 3m 18s&#x20;

66 actionable tasks: 61 executed, 5 from cache&#x20;

**Error:&#x20;**&#x50;rocess completed with exit code 1.