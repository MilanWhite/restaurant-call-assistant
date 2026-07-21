package com.example.restaurant_call_assistant.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // With READ_CALL_LOG Android sends a second PHONE_STATE broadcast containing
                // EXTRA_INCOMING_NUMBER. Briefly defer the companion broadcast that lacks the
                // extra so it cannot win the race, while still supporting genuinely private calls.
                if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                    val pendingResult = goAsync()
                    IncomingCallCoordinator.onNumberUnavailable(context) { pendingResult.finish() }
                    return
                }
                IncomingCallCoordinator.onRinging(
                    context,
                    intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                )
            }
            TelephonyManager.EXTRA_STATE_IDLE -> IncomingCallCoordinator.onCallEnded()
        }
    }
}
