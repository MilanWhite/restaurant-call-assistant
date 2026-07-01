package com.example.restaurant_call_assistant.data

object PhoneNumbers {
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") && digits.isNotEmpty() -> "+$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            digits.length == 10 -> "+1$digits"
            else -> digits
        }
    }

    fun looseKey(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun matches(candidate: String?, stored: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) return false
        if (normalizedCandidate == stored) return true
        val candidateLoose = looseKey(normalizedCandidate)
        val storedLoose = looseKey(stored)
        return candidateLoose.length == 10 && candidateLoose == storedLoose
    }
}
