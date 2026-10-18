# Private sync file isolated JVM verification

This runner compiles the real `PrivateSyncFiles` and `LanSyncSecurity` production
sources and the two test classes with Kotlin 2.2.20/JDK 17 and real OkHttp TLS.
It does not configure or compile the rest of the application.

The recorded Linux run passed 18 tests; the three native Windows cases were
assumption-skipped, not passed. `RESULTS.json` records the platform, precise
counts, skipped cases, source hashes, runner hashes and command hashes.
`dependencies.json` records every downloaded Maven Central URL and SHA-256.
No JARs, compiled classes, generated certificate keys or synthetic private state
are included in this evidence directory.

From the repository root, run (using a separate scratch directory):

```sh
python3 docs/evidence/private-sync-round62/isolated-jvm/run_isolated.py \
  --repo . --work-dir /tmp/ikna-private-acl-check --java-home "$JAVA_HOME"
```

On Windows, use Python and a Windows scratch directory. The three actual-provider
cases execute there. The inherited-ACL fixture resolves the well-known Everyone
SID with Windows PowerShell; the junction fixture uses `cmd.exe /c mklink /J`.
All permission and junction mutations are confined to newly created synthetic
temporary files/directories. The original packaged application selftest remains
a separate required Windows acceptance check.

The runner downloads only missing dependencies, checks every hash before use,
and stores regenerated commands/logs/results in the selected work directory.
It fails if the JVM is not JDK 17 or any compilation/test fails.
