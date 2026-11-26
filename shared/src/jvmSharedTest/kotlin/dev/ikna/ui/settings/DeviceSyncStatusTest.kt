package dev.ikna.ui.settings

import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.ProfileConsentProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceSyncStatusTest {
    private fun state(caughtUp: Boolean, pending: Boolean) = DeviceSyncController.State(
        enabled = true, running = true, peers = 1, lastSuccessAtMillis = 123L,
        profileConsent = ProfileConsentProtocol.State(complete = true,
            journalCaughtUp = caughtUp, journalPending = pending))

    @Test fun `completed consent and old successful exchange do not hide pending history`() {
        assertEquals(DeviceSyncStatus.SYNCING, deviceSyncStatus(state(false, true)))
        assertEquals(DeviceSyncStatus.WAITING, deviceSyncStatus(state(false, false)))
        assertEquals(DeviceSyncStatus.UP_TO_DATE, deviceSyncStatus(state(true, false)))
    }
    @Test fun `offline paused disabled and errors override caught up snapshot`() {
        val ready = state(true, false)
        assertEquals(DeviceSyncStatus.WAITING, deviceSyncStatus(ready.copy(running = false)))
        assertEquals(DeviceSyncStatus.OFF, deviceSyncStatus(ready.copy(enabled = false)))
        assertEquals(DeviceSyncStatus.UNPAIRED, deviceSyncStatus(ready.copy(peers = 0)))
        assertEquals(DeviceSyncStatus.ERROR, deviceSyncStatus(ready.copy(error = "connection_failed")))
        assertEquals(DeviceSyncStatus.WAITING, deviceSyncStatus(ready.copy(profileConsent = ProfileConsentProtocol.State())))
    }
    @Test fun `active connection and transfer stages expose progress bar fractions`() {
        val base = state(true, false)
        assertNull(deviceSyncProgress(base))
        val connecting = base.copy(stage = DeviceSyncController.SyncStage.PAIRING_CONNECTING, stageAttempt = 1, stageTotal = 2)
        assertEquals(DeviceSyncStatus.CONNECTING, deviceSyncStatus(connecting))
        val connectingProgress = assertNotNull(deviceSyncProgress(connecting, "Laptop"))
        assertTrue(connectingProgress.fraction in 0.1f..0.4f)

        val exchanging = base.copy(stage = DeviceSyncController.SyncStage.EXCHANGING_PROFILES)
        assertEquals(DeviceSyncStatus.SYNCING, deviceSyncStatus(exchanging))
        val applying = base.copy(stage = DeviceSyncController.SyncStage.APPLYING_PROFILE)
        assertTrue(assertNotNull(deviceSyncProgress(applying)).fraction > assertNotNull(deviceSyncProgress(exchanging)).fraction)
    }
}
