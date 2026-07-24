package com.example.restaurant_call_assistant.call

internal data class CallerUpdate(
    val sessionId: Long,
    val phoneNumber: String?
)

internal data class PendingCallResolution(
    val token: Long,
    val phoneNumber: String?,
    val ringingStartedAt: Long
)

/**
 * Reduces Android's aggregate phone-state events to overlay updates.
 *
 * A caller that arrives while another call is active stays pending until OFFHOOK.
 * The coordinator then checks the call log before deciding whether that caller was
 * answered or merely stopped ringing.
 */
internal class IncomingCallSession(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val nextSessionId: () -> Long = { System.currentTimeMillis() }
) {
    private var sessionId: Long = 0L
    private var lastSessionId: Long = 0L
    private var knownNumber: String? = null
    private var popupDispatched = false
    private var activeCallExists = false
    private var pendingCall: PendingCallResolution? = null
    private var pendingResolutionIssued = false

    fun needsUnknownResolution(): Boolean {
        return if (activeCallExists) {
            pendingCall?.phoneNumber == null
        } else {
            knownNumber == null && !popupDispatched
        }
    }

    fun onRinging(phoneNumber: String?): CallerUpdate? {
        val number = phoneNumber?.trim()?.takeIf(String::isNotEmpty)
        if (activeCallExists) {
            val existing = pendingCall
            if (existing == null) {
                pendingCall = PendingCallResolution(
                    token = newSessionId(),
                    phoneNumber = number,
                    ringingStartedAt = now()
                )
            } else if (number != null && number != existing.phoneNumber) {
                pendingCall = existing.copy(phoneNumber = number)
            }
            return null
        }

        if (sessionId == 0L) sessionId = newSessionId()
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

    fun onOffhook(): PendingCallResolution? {
        if (sessionId == 0L && pendingCall == null) return null
        activeCallExists = true
        val pending = pendingCall ?: return null
        if (pendingResolutionIssued) return null
        pendingResolutionIssued = true
        return pending
    }

    fun resolvePending(token: Long, wasUnanswered: Boolean): CallerUpdate? {
        val pending = pendingCall?.takeIf { it.token == token } ?: return null
        pendingCall = null
        pendingResolutionIssued = false
        if (wasUnanswered) return null

        sessionId = newSessionId()
        knownNumber = pending.phoneNumber
        popupDispatched = true
        activeCallExists = true
        return CallerUpdate(sessionId, pending.phoneNumber)
    }

    fun onCallEnded() {
        sessionId = 0L
        knownNumber = null
        popupDispatched = false
        activeCallExists = false
        pendingCall = null
        pendingResolutionIssued = false
    }

    private fun newSessionId(): Long {
        val generated = nextSessionId().coerceAtLeast(1L)
        return maxOf(generated, lastSessionId + 1L).also { lastSessionId = it }
    }
}
