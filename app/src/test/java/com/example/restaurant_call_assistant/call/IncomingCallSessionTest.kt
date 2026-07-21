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

}
