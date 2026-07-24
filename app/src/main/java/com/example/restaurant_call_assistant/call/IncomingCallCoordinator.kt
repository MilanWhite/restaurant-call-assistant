package com.example.restaurant_call_assistant.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.util.Log
import com.example.restaurant_call_assistant.data.CallTriggerMode
import com.example.restaurant_call_assistant.data.LocalStore
import com.example.restaurant_call_assistant.data.PhoneNumbers
import com.example.restaurant_call_assistant.data.WhitelistMode
import com.example.restaurant_call_assistant.overlay.ReservationOverlayService
import java.util.concurrent.Executors

object IncomingCallCoordinator {
    private const val TAG = "IncomingCall"
    private const val UNKNOWN_CALLER_DELAY_MS = 750L
    private const val PENDING_CALL_VERIFICATION_MS = 2_000L
    private const val CALL_LOG_CLOCK_TOLERANCE_MS = 1_000L
    private val session = IncomingCallSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callLogExecutor = Executors.newSingleThreadExecutor()
    private var pendingUnknownCaller: Runnable? = null
    private var pendingCallVerification: Runnable? = null
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

    fun onOffhook(context: Context) {
        val completions: List<() -> Unit>
        val unknownUpdate: CallerUpdate?
        val pending: PendingCallResolution?
        synchronized(this) {
            if (pendingUnknownCaller != null) {
                mainHandler.removeCallbacks(pendingUnknownCaller!!)
                pendingUnknownCaller = null
                unknownUpdate = session.onRinging(null)
                completions = pendingCompletions.toList()
                pendingCompletions.clear()
            } else {
                unknownUpdate = null
                completions = emptyList()
            }
            pending = session.onOffhook()
        }
        completions.forEach { it() }
        unknownUpdate?.let { dispatch(context, it) }
        pending ?: return

        val appContext = context.applicationContext
        pendingCallVerification?.let(mainHandler::removeCallbacks)
        pendingCallVerification = Runnable {
            callLogExecutor.execute {
                val wasUnanswered = wasUnanswered(appContext, pending)
                mainHandler.post {
                    val update = synchronized(this) {
                        pendingCallVerification = null
                        session.resolvePending(
                            token = pending.token,
                            wasUnanswered = wasUnanswered != false
                        )
                    }
                    update?.let { dispatch(appContext, it) }
                }
            }
        }.also { mainHandler.postDelayed(it, PENDING_CALL_VERIFICATION_MS) }
    }

    fun onCallEnded() {
        val completions: List<() -> Unit>
        synchronized(this) {
            completions = cancelUnknownCallerLocked()
            pendingCallVerification?.let(mainHandler::removeCallbacks)
            pendingCallVerification = null
            session.onCallEnded()
        }
        completions.forEach { it() }
    }

    private fun cancelUnknownCallerLocked(): List<() -> Unit> {
        pendingUnknownCaller?.let(mainHandler::removeCallbacks)
        pendingUnknownCaller = null
        return pendingCompletions.toList().also { pendingCompletions.clear() }
    }

    /**
     * Returns true for a missed/rejected waiting call, false when no such entry
     * exists, and null when the call log cannot be checked. Unknown is handled
     * conservatively so an old overlay is never overwritten without evidence.
     */
    private fun wasUnanswered(context: Context, pending: PendingCallResolution): Boolean? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return runCatching {
            val unansweredTypes = intArrayOf(
                CallLog.Calls.MISSED_TYPE,
                CallLog.Calls.REJECTED_TYPE,
                CallLog.Calls.BLOCKED_TYPE,
                CallLog.Calls.ANSWERED_EXTERNALLY_TYPE
            )
            val uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "20")
                .build()
            val selection = buildString {
                append("${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} IN (")
                append(unansweredTypes.joinToString(",") { "?" })
                append(")")
            }
            val selectionArgs = buildList {
                add((pending.ringingStartedAt - CALL_LOG_CLOCK_TOLERANCE_MS).toString())
                unansweredTypes.forEach { add(it.toString()) }
            }.toTypedArray()

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.NUMBER),
                selection,
                selectionArgs,
                CallLog.Calls.DEFAULT_SORT_ORDER
            ) ?: return@runCatching null
            cursor.use {
                val numberColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                while (cursor.moveToNext()) {
                    val loggedNumber = cursor.getString(numberColumn)
                    if (pending.phoneNumber == null ||
                        PhoneNumbers.looseKey(loggedNumber) == PhoneNumbers.looseKey(pending.phoneNumber)
                    ) {
                        return@runCatching true
                    }
                }
            }
            false
        }.onFailure { error ->
            Log.w(TAG, "Unable to verify the waiting call in the call log", error)
        }.getOrNull()
    }
}
