package dev.ikna.sync

/** Safe category for peers that cannot honor the current consent/exchange contract. */
class UnsupportedSyncProtocolException : IllegalArgumentException("Unsupported synchronization protocol")
