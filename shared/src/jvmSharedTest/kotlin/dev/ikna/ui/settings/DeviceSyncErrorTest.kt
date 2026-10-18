package dev.ikna.ui.settings
import kotlin.test.Test
import kotlin.test.assertEquals
class DeviceSyncErrorTest {
    @Test fun `size limits are actionable rather than network or content conflicts`() {
        assertEquals(DeviceSyncError.PROFILE_TOO_LARGE, deviceSyncError("profile-too-large"))
        assertEquals(DeviceSyncError.PROFILE_TOO_LARGE, deviceSyncError("payload_too_large"))
        assertEquals(DeviceSyncError.INCOMPATIBLE_PROTOCOL, deviceSyncError("unsupported_sync_protocol"))
        assertEquals(DeviceSyncError.JOURNAL_ITEM_TOO_LARGE, deviceSyncError("journal_item_too_large"))
        assertEquals(DeviceSyncError.JOURNAL_RECONCILIATION, deviceSyncError("journal_reconciliation_required"))
        assertEquals(DeviceSyncError.NETWORK, deviceSyncError("connection_failed"))
        assertEquals(DeviceSyncError.MERGE_FAILED, deviceSyncError("private synthetic exception text"))
    }
}
