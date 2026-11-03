package tech.kelma.app

import platform.Foundation.NSUUID

// NSUUID renders uppercase hex. KelmaSync accepts only canonical lowercase UUIDs for
// note-mark, card-flag, and review-retraction intents, and echoes them back verbatim.
actual fun randomUuidString(): String = NSUUID().UUIDString().lowercase()
