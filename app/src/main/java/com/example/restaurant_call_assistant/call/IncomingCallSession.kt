package com.example.restaurant_call_assistant.call

internal data class CallerUpdate(
    val sessionId: Long,
    val phoneNumber: String?
)

/**
 * Reduces all Android call sources to one update stream for the active call.
 * A known number always wins over a missing number, independent of event order.
 */
internal class IncomingCallSession(
    private val nextSessionId: () -> Long = { System.currentTimeMillis() }
) {
    private var sessionId: Long = 0L
    private var knownNumber: String? = null
    private var popupDispatched = false

    fun needsUnknownResolution(): Boolean = knownNumber == null && !popupDispatched

    fun onRinging(phoneNumber: String?): CallerUpdate? {
        if (sessionId == 0L) sessionId = nextSessionId().coerceAtLeast(1L)

        val number = phoneNumber?.trim()?.takeIf(String::isNotEmpty)
        if (number != null) {
            if (popupDispatched && number == knownNumber) return null
            knownNumber = number
            popupDispatched = true
            return CallerUpdate(sessionId, number)
        }

        if (knownNumber != null || popupDispatched) return null
        popupDispatched = true
        return CallerUpdate(sessionId, null)
    }

    fun onCallEnded() {
        sessionId = 0L
        knownNumber = null
        popupDispatched = false
    }
}
