package com.example.restaurant_call_assistant.call

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.restaurant_call_assistant.data.CallTriggerMode
import com.example.restaurant_call_assistant.data.LocalStore
import com.example.restaurant_call_assistant.data.WhitelistMode
import com.example.restaurant_call_assistant.overlay.ReservationOverlayService

object IncomingCallCoordinator {
    private const val TAG = "IncomingCall"
    private const val UNKNOWN_CALLER_DELAY_MS = 750L
    private val session = IncomingCallSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingUnknownCaller: Runnable? = null
    private val pendingCompletions = mutableListOf<() -> Unit>()

    fun onRinging(context: Context, phoneNumber: String?) {
        val completions: List<() -> Unit>
        val update: CallerUpdate?
        synchronized(this) {
            completions = cancelUnknownCallerLocked()
            update = session.onRinging(phoneNumber)
        }
        completions.forEach { it() }
        update?.let { dispatch(context, it) }
    }

    fun onNumberUnavailable(context: Context, completion: () -> Unit) {
        synchronized(this) {
            if (!session.needsUnknownResolution()) {
                completion()
                return
            }
            pendingCompletions += completion
            if (pendingUnknownCaller != null) return

            val appContext = context.applicationContext
            pendingUnknownCaller = Runnable {
                val update: CallerUpdate?
                val completions: List<() -> Unit>
                synchronized(this) {
                    pendingUnknownCaller = null
                    update = session.onRinging(null)
                    completions = pendingCompletions.toList()
                    pendingCompletions.clear()
                }
                update?.let { dispatch(appContext, it) }
                completions.forEach { it() }
            }.also { mainHandler.postDelayed(it, UNKNOWN_CALLER_DELAY_MS) }
        }
    }

    private fun dispatch(context: Context, update: CallerUpdate) {
        val appContext = context.applicationContext
        val store = LocalStore(appContext)
        val settings = store.getSettings()

        if (!settings.popupEnabled || settings.callTriggerMode == CallTriggerMode.OUTGOING) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) return
        if (update.phoneNumber == null && !settings.showForUnknownNumbers) return

        val isListed = store.isWhitelisted(update.phoneNumber)
        val shouldShow = when (settings.whitelistMode) {
            WhitelistMode.IGNORE_LISTED -> !isListed
            WhitelistMode.ONLY_LISTED -> true
        }
        if (!shouldShow) return

        val intent = ReservationOverlayService.showIntent(
            context = appContext,
            callerNumber = update.phoneNumber,
            startMinimized = true,
            callSessionId = update.sessionId
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { error ->
            Log.e(TAG, "Unable to display the incoming-call reservation popup", error)
        }
    }

    fun onCallEnded() {
        val completions: List<() -> Unit>
        synchronized(this) {
            completions = cancelUnknownCallerLocked()
            session.onCallEnded()
        }
        completions.forEach { it() }
    }

    private fun cancelUnknownCallerLocked(): List<() -> Unit> {
        pendingUnknownCaller?.let(mainHandler::removeCallbacks)
        pendingUnknownCaller = null
        return pendingCompletions.toList().also { pendingCompletions.clear() }
    }
}
