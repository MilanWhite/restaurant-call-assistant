package com.example.restaurant_call_assistant.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class ReservationCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        // This app never blocks or silences calls. Respond before doing any app work so
        // caller lookup and popup rendering can never delay the phone from ringing.
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        )

        val phoneNumber = callDetails.handle
            ?.takeIf { it.scheme.equals("tel", ignoreCase = true) }
            ?.schemeSpecificPart
        IncomingCallCoordinator.onRinging(this, phoneNumber)
    }
}
