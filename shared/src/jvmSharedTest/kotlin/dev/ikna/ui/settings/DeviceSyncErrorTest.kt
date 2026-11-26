package dev.ikna.ui.settings

import dev.ikna.ui.text.STRINGS_EN
import dev.ikna.ui.text.STRINGS_RU
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test fun `specific network, invitation, trust, and profile errors stay distinct`() {
        assertEquals(DeviceSyncError.CONNECTION_TIMEOUT, deviceSyncError("connection_timeout"))
        assertEquals(DeviceSyncError.CONNECTION_REFUSED, deviceSyncError("connection_refused"))
        assertEquals(DeviceSyncError.NETWORK_UNREACHABLE, deviceSyncError("network_unreachable"))
        assertEquals(DeviceSyncError.PEER_CLOSED_CONNECTION, deviceSyncError("peer_closed_connection"))
        assertEquals(DeviceSyncError.PEER_CLOSED_CONNECTION, deviceSyncError("connection_interrupted"))
        assertEquals(DeviceSyncError.UNTRUSTED_DEVICE, deviceSyncError("tls_trust_failed"))
        assertEquals(DeviceSyncError.UNTRUSTED_DEVICE, deviceSyncError("unpaired_device"))
        assertEquals(DeviceSyncError.EXPIRED_INVITATION, deviceSyncError("expired_invitation"))
        assertEquals(DeviceSyncError.INVALID_INVITATION, deviceSyncError("invalid_invitation"))
        assertEquals(DeviceSyncError.ALREADY_PAIRED, deviceSyncError("already_paired"))
        assertEquals(DeviceSyncError.PORT_UNAVAILABLE, deviceSyncError("port_unavailable"))
        assertEquals(DeviceSyncError.PROFILE_CONFLICT, deviceSyncError("profile-conflict-or-stale", hasProfileConflicts = true))
        assertEquals(DeviceSyncError.PROFILE_STALE, deviceSyncError("profile-conflict-or-stale", hasProfileConflicts = false))
        assertEquals(DeviceSyncError.PROFILE_STALE, deviceSyncError("stale-proposal"))
        assertEquals(DeviceSyncError.PROFILE_STALE, deviceSyncError("proposal-expired"))
    }

    @Test fun `every sync error has both a specific title and a concrete solution`() {
        for (error in DeviceSyncError.entries) {
            val titleKey = syncErrorTitleKey(error)
            val fixKey = syncErrorFixKey(error)
            assertTrue(STRINGS_EN[titleKey]?.isNotBlank() == true, "Missing EN title for $error ($titleKey)")
            assertTrue(STRINGS_RU[titleKey]?.isNotBlank() == true, "Missing RU title for $error ($titleKey)")
            assertTrue(STRINGS_EN[fixKey]?.isNotBlank() == true, "Missing EN fix for $error ($fixKey)")
            assertTrue(STRINGS_RU[fixKey]?.isNotBlank() == true, "Missing RU fix for $error ($fixKey)")
        }
    }
}
