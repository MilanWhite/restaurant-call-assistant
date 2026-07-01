package com.example.restaurant_call_assistant.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.restaurant_call_assistant.data.Reservation
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String
)

data class CalendarCreateResult(
    val status: CalendarCreateStatus,
    val eventId: String? = null,
    val errorMessage: String? = null
)

enum class CalendarCreateStatus {
    CREATED,
    OPENED_FALLBACK,
    FAILED
}

class CalendarRepository(private val context: Context) {
    fun writableCalendars(): List<CalendarAccount> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        return context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null
        )?.use { cursor ->
            val calendars = mutableListOf<CalendarAccount>()
            while (cursor.moveToNext()) {
                val accessLevel = cursor.getInt(3)
                if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    calendars.add(
                        CalendarAccount(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1) ?: "Calendar",
                            accountName = cursor.getString(2) ?: ""
                        )
                    )
                }
            }
            calendars
        }.orEmpty()
    }

    fun createOrOpenFallback(reservation: Reservation, preferredCalendarId: Long?): CalendarCreateResult {
        val googleCalendars = writableCalendars()
        val selectedCalendarId = googleCalendars.firstOrNull { it.id == preferredCalendarId }?.id
            ?: googleCalendars.firstOrNull()?.id
        if (selectedCalendarId == null) {
            return openFallback(reservation, "No writable Google calendar was available.")
        }

        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return openFallback(reservation, "Calendar write permission has not been granted.")
        }

        return runCatching {
            val startMillis = reservation.startMillis()
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, startMillis + reservation.durationMinutes * 60_000L)
                put(CalendarContract.Events.TITLE, reservation.eventTitle())
                put(CalendarContract.Events.DESCRIPTION, reservation.eventDescription())
                put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment
            if (eventId.isNullOrBlank()) {
                openFallback(reservation, "Calendar Provider did not return an event id.")
            } else {
                CalendarCreateResult(CalendarCreateStatus.CREATED, eventId = eventId)
            }
        }.getOrElse { error ->
            openFallback(reservation, error.localizedMessage ?: "Calendar insert failed.")
        }
    }

    fun openEvent(eventId: String) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
        val intent = Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openFallback(reservation: Reservation, reason: String): CalendarCreateResult {
        return runCatching {
            val startMillis = reservation.startMillis()
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + reservation.durationMinutes * 60_000L)
                .putExtra(CalendarContract.Events.TITLE, reservation.eventTitle())
                .putExtra(CalendarContract.Events.DESCRIPTION, reservation.eventDescription())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val googleIntent = Intent(intent).setPackage(GOOGLE_CALENDAR_PACKAGE)
            context.startActivity(
                if (googleIntent.resolveActivity(context.packageManager) != null) {
                    googleIntent
                } else {
                    intent
                }
            )
            CalendarCreateResult(CalendarCreateStatus.OPENED_FALLBACK, errorMessage = reason)
        }.getOrElse { fallbackError ->
            CalendarCreateResult(
                CalendarCreateStatus.FAILED,
                errorMessage = "$reason Fallback failed: ${fallbackError.localizedMessage ?: "unknown error"}"
            )
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"
    }
}

fun Reservation.eventTitle(): String {
    return "$customerName - ${phoneNumber.orEmpty()} - $reservationTime"
}

fun Reservation.eventDescription(): String = buildString {
    appendLine("Customer: $customerName")
    appendLine("Phone: ${phoneNumber.orEmpty()}")
    partySize?.let { appendLine("Party size: $it") }
    eventType?.let { appendLine("Event type: $it") }
    append("Notes: ${notes.orEmpty()}")
}

fun Reservation.startMillis(): Long {
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    parser.isLenient = false
    return parser.parse("$reservationDate $reservationTime")?.time
        ?: throw IllegalArgumentException("Invalid reservation date or time.")
}
