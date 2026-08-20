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
            reservationTime = "6:30 PM",
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

        assertEquals("Alex - 416-555-1234 - 6:30 PM", reservation.eventTitle())
    }

    @Test
    fun eventDescription_containsSelectedEventType() {
        val reservation = Reservation(
            customerName = "Alex",
            phoneNumber = "416-555-1234",
            reservationDate = "2026-07-21",
            reservationTime = "6:30 PM",
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

    @Test
    fun eventDescription_containsPeopleBreakdownAndPreference() {
        val reservation = Reservation(
            customerName = "Alex",
            phoneNumber = "416-555-1234",
            reservationDate = "2026-07-21",
            reservationTime = "6:30 PM",
            durationMinutes = 120,
            partySize = 5,
            tablePreference = "Patio",
            eventType = "Birthday",
            notes = null,
            internalNotes = null,
            calendarEventId = null,
            status = ReservationStatus.DRAFT,
            errorMessage = null,
            adultCount = 3,
            childCount = 2
        )

        assertEquals(
            "Customer: Alex\nPhone: 416-555-1234\nParty size: 5\nAdults: 3\nChildren: 2\nEvent type: Birthday\nPreference: Patio\nNotes: ",
            reservation.eventDescription()
        )
    }
}
