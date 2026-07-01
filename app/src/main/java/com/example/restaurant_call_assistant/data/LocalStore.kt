package com.example.restaurant_call_assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reservation_helper", Context.MODE_PRIVATE)

    fun getReservations(): List<Reservation> {
        val array = JSONArray(prefs.getString(KEY_RESERVATIONS, "[]"))
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toReservation())
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun saveReservation(reservation: Reservation) {
        val next = getReservations().filterNot { it.id == reservation.id }.toMutableList()
        next.add(reservation.copy(updatedAt = System.currentTimeMillis()))
        prefs.edit().putString(KEY_RESERVATIONS, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun deleteReservation(id: Long) {
        val next = getReservations().filterNot { it.id == id }
        prefs.edit().putString(KEY_RESERVATIONS, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun getWhitelist(): List<WhitelistEntry> {
        val array = JSONArray(prefs.getString(KEY_WHITELIST, "[]"))
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toWhitelistEntry())
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun saveWhitelistEntry(entry: WhitelistEntry) {
        val next = getWhitelist().filterNot { it.id == entry.id }.toMutableList()
        next.add(entry.copy(updatedAt = System.currentTimeMillis()))
        prefs.edit().putString(KEY_WHITELIST, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun deleteWhitelistEntry(id: Long) {
        val next = getWhitelist().filterNot { it.id == id }
        prefs.edit().putString(KEY_WHITELIST, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun isWhitelisted(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        return getWhitelist().any { PhoneNumbers.matches(phoneNumber, it.normalizedPhoneNumber) }
    }

    fun getSettings(): AppSettings {
        val json = JSONObject(prefs.getString(KEY_SETTINGS, "{}").orEmpty().ifBlank { "{}" })
        return AppSettings(
            selectedCalendarId = if (json.has("selectedCalendarId") && !json.isNull("selectedCalendarId")) {
                json.optLong("selectedCalendarId")
            } else {
                null
            },
            selectedCalendarName = json.optString("selectedCalendarName").ifBlank { null },
            popupEnabled = json.optBoolean("popupEnabled", true),
            defaultDurationMinutes = json.optInt("defaultDurationMinutes", 240).coerceAtLeast(15),
            showForUnknownNumbers = json.optBoolean("showForUnknownNumbers", true),
            whitelistMode = enumValueOrDefault(json.optString("whitelistMode"), WhitelistMode.IGNORE_LISTED),
            callTriggerMode = enumValueOrDefault(json.optString("callTriggerMode"), CallTriggerMode.INCOMING),
            autoFillCallerNumber = json.optBoolean("autoFillCallerNumber", true),
            keepPopupOpenAfterCall = json.optBoolean("keepPopupOpenAfterCall", true)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, settings.toJson().toString()).apply()
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }

    private fun Reservation.toJson() = JSONObject()
        .put("id", id)
        .put("customerName", customerName)
        .put("phoneNumber", phoneNumber)
        .put("reservationDate", reservationDate)
        .put("reservationTime", reservationTime)
        .put("durationMinutes", durationMinutes)
        .put("partySize", partySize)
        .put("tablePreference", tablePreference)
        .put("eventType", eventType)
        .put("notes", notes)
        .put("internalNotes", internalNotes)
        .put("calendarEventId", calendarEventId)
        .put("status", status.name)
        .put("errorMessage", errorMessage)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toReservation() = Reservation(
        id = optLong("id"),
        customerName = optString("customerName"),
        phoneNumber = optString("phoneNumber").ifBlank { null },
        reservationDate = optString("reservationDate"),
        reservationTime = optString("reservationTime"),
        durationMinutes = optInt("durationMinutes", 240),
        partySize = if (has("partySize") && !isNull("partySize")) optInt("partySize") else null,
        tablePreference = optString("tablePreference").ifBlank { null },
        eventType = optString("eventType").ifBlank { null },
        notes = optString("notes").ifBlank { null },
        internalNotes = optString("internalNotes").ifBlank { null },
        calendarEventId = optString("calendarEventId").ifBlank { null },
        status = enumValueOrDefault(optString("status"), ReservationStatus.DRAFT),
        errorMessage = optString("errorMessage").ifBlank { null },
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

    private fun WhitelistEntry.toJson() = JSONObject()
        .put("id", id)
        .put("displayName", displayName)
        .put("rawPhoneNumber", rawPhoneNumber)
        .put("normalizedPhoneNumber", normalizedPhoneNumber)
        .put("note", note)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toWhitelistEntry() = WhitelistEntry(
        id = optLong("id"),
        displayName = optString("displayName").ifBlank { null },
        rawPhoneNumber = optString("rawPhoneNumber"),
        normalizedPhoneNumber = optString("normalizedPhoneNumber"),
        note = optString("note").ifBlank { null },
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

    private fun AppSettings.toJson() = JSONObject()
        .put("selectedCalendarId", selectedCalendarId)
        .put("selectedCalendarName", selectedCalendarName)
        .put("popupEnabled", popupEnabled)
        .put("defaultDurationMinutes", defaultDurationMinutes)
        .put("showForUnknownNumbers", showForUnknownNumbers)
        .put("whitelistMode", whitelistMode.name)
        .put("callTriggerMode", callTriggerMode.name)
        .put("autoFillCallerNumber", autoFillCallerNumber)
        .put("keepPopupOpenAfterCall", keepPopupOpenAfterCall)

    companion object {
        private const val KEY_RESERVATIONS = "reservations"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
