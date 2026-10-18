package dev.ikna.ui.settings

import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.ProfileConsentProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
