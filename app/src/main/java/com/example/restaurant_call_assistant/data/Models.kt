package com.example.restaurant_call_assistant.data

data class Reservation(
    val id: Long = System.currentTimeMillis(),
    val customerName: String,
    val phoneNumber: String?,
    val reservationDate: String,
    val reservationTime: String,
    val durationMinutes: Int,
    val partySize: Int?,
    val tablePreference: String?,
    val eventType: String? = null,
    val notes: String?,
    val internalNotes: String?,
    val calendarEventId: String?,
    val status: ReservationStatus,
    val errorMessage: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ReservationStatus {
    DRAFT,
    CREATED,
    FAILED,
    OPENED_IN_CALENDAR
}

data class WhitelistEntry(
    val id: Long = System.currentTimeMillis(),
    val displayName: String?,
    val rawPhoneNumber: String,
    val normalizedPhoneNumber: String,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AppSettings(
    val selectedCalendarId: Long? = null,
    val selectedCalendarName: String? = null,
    val popupEnabled: Boolean = true,
    val defaultDurationMinutes: Int = 240,
    val showForUnknownNumbers: Boolean = true,
    val whitelistMode: WhitelistMode = WhitelistMode.IGNORE_LISTED,
    val callTriggerMode: CallTriggerMode = CallTriggerMode.INCOMING,
    val autoFillCallerNumber: Boolean = true,
    val keepPopupOpenAfterCall: Boolean = true
)

enum class WhitelistMode {
    IGNORE_LISTED,
    ONLY_LISTED
}

enum class CallTriggerMode {
    INCOMING,
    OUTGOING,
    BOTH
}
