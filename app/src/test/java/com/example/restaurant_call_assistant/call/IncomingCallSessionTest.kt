package com.example.restaurant_call_assistant.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingCallSessionTest {
    @Test
    fun `blank then number updates the same call session`() {
        val session = IncomingCallSession { 41L }

        assertEquals(CallerUpdate(41L, null), session.onRinging(null))
        assertEquals(CallerUpdate(41L, "6505551212"), session.onRinging("6505551212"))
    }

    @Test
    fun `number then blank keeps the known number`() {
        val session = IncomingCallSession { 42L }

        assertEquals(CallerUpdate(42L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onRinging(null))
    }

    @Test
    fun `duplicate numbered events are ignored`() {
        val session = IncomingCallSession { 43L }

        assertEquals(CallerUpdate(43L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onRinging("6505551212"))
    }

    @Test
    fun `ending a call creates a new session for the next call`() {
        var id = 50L
        val session = IncomingCallSession { id++ }

        assertEquals(CallerUpdate(50L, "6505551212"), session.onRinging("6505551212"))
        session.onCallEnded()
        assertEquals(CallerUpdate(51L, "6505551212"), session.onRinging("6505551212"))
    }

    @Test
    fun `a different nonblank number upgrades the active session`() {
        val session = IncomingCallSession { 52L }

        assertEquals(CallerUpdate(52L, "6505551212"), session.onRinging("6505551212"))
        assertEquals(CallerUpdate(52L, "4165551234"), session.onRinging("4165551234"))
    }

    @Test
    fun `waiting caller stays pending until offhook and verification`() {
        var id = 60L
        val session = IncomingCallSession(nextSessionId = { id++ }, now = { 1_000L })

        assertEquals(CallerUpdate(60L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onOffhook())

        assertNull(session.onRinging("4165551234"))
        val pending = session.onOffhook()
        assertEquals(PendingCallResolution(61L, "4165551234", 1_000L), pending)
        assertEquals(
            CallerUpdate(62L, "4165551234"),
            session.resolvePending(pending!!.token, wasUnanswered = false)
        )
    }

    @Test
    fun `waiting caller that was missed does not replace active caller`() {
        var id = 70L
        val session = IncomingCallSession(nextSessionId = { id++ }, now = { 2_000L })

        assertEquals(CallerUpdate(70L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onOffhook())
        assertNull(session.onRinging("4165551234"))

        val pending = session.onOffhook()
        assertEquals(PendingCallResolution(71L, "4165551234", 2_000L), pending)
        assertNull(session.resolvePending(pending!!.token, wasUnanswered = true))
        assertNull(session.onOffhook())
    }

    @Test
    fun `known number upgrades an unknown pending waiting call`() {
        var id = 80L
        val session = IncomingCallSession(nextSessionId = { id++ }, now = { 3_000L })

        assertEquals(CallerUpdate(80L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onOffhook())
        assertNull(session.onRinging(null))
        assertNull(session.onRinging("4165551234"))

        assertEquals(
            PendingCallResolution(81L, "4165551234", 3_000L),
            session.onOffhook()
        )
    }

    @Test
    fun `ending while a waiting caller rings discards the pending caller`() {
        var id = 90L
        val session = IncomingCallSession(nextSessionId = { id++ }, now = { 4_000L })

        assertEquals(CallerUpdate(90L, "6505551212"), session.onRinging("6505551212"))
        assertNull(session.onOffhook())
        assertNull(session.onRinging("4165551234"))

        session.onCallEnded()

        assertNull(session.onOffhook())
        assertEquals(CallerUpdate(92L, "2125550100"), session.onRinging("2125550100"))
    }
}
