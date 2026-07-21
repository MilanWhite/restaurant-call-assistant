package com.example.restaurant_call_assistant.calendar

import com.example.restaurant_call_assistant.data.Reservation
import com.example.restaurant_call_assistant.data.ReservationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRepositoryTest {
    @Test
    fun eventTitle_containsNameNumberAndTime() {
        val reservation = Reservation(
            customerName = "Alex",
            phoneNumber = "416-555-1234",
            reservationDate = "2026-07-21",
            reservationTime = "18:30",
            durationMinutes = 120,
            partySize = 4,
            tablePreference = null,
            eventType = "Wedding",
            notes = null,
            internalNotes = null,
            calendarEventId = null,
            status = ReservationStatus.DRAFT,
            errorMessage = null
        )

        assertEquals("Alex - 416-555-1234 - 18:30", reservation.eventTitle())
    }

    @Test
    fun eventDescription_containsSelectedEventType() {
        val reservation = Reservation(
            customerName = "Alex",
            phoneNumber = "416-555-1234",
            reservationDate = "2026-07-21",
            reservationTime = "18:30",
            durationMinutes = 120,
            partySize = 4,
            tablePreference = null,
            eventType = "Wedding",
            notes = "Window table",
            internalNotes = null,
            calendarEventId = null,
            status = ReservationStatus.DRAFT,
            errorMessage = null
        )

        assertEquals(
            "Customer: Alex\nPhone: 416-555-1234\nParty size: 4\nEvent type: Wedding\nNotes: Window table",
            reservation.eventDescription()
        )
    }

}
