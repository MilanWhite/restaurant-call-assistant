package com.example.restaurant_call_assistant.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.restaurant_call_assistant.MainActivity
import com.example.restaurant_call_assistant.R
import com.example.restaurant_call_assistant.calendar.CalendarCreateStatus
import com.example.restaurant_call_assistant.calendar.CalendarRepository
import com.example.restaurant_call_assistant.data.LocalStore
import com.example.restaurant_call_assistant.data.Reservation
import com.example.restaurant_call_assistant.data.ReservationStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReservationOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var store: LocalStore
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isMinimized = false
    private var activeCallSessionId = NO_CALL_SESSION
    private var callerNameInput: EditText? = null
    private var callerPhoneInput: EditText? = null
    private var callerNameEdited = false
    private var callerPhoneEdited = false
    private var applyingCallerUpdate = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = LocalStore(this)
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> stopSelf()
            else -> showOverlay(
                callerNumber = intent?.getStringExtra(EXTRA_PHONE),
                startMinimized = intent?.getBooleanExtra(EXTRA_START_MINIMIZED, false) == true,
                callSessionId = intent?.getLongExtra(EXTRA_CALL_SESSION_ID, NO_CALL_SESSION)
                    ?: NO_CALL_SESSION
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        callerNameInput = null
        callerPhoneInput = null
        activeCallSessionId = NO_CALL_SESSION
        super.onDestroy()
    }

    private fun showOverlay(callerNumber: String?, startMinimized: Boolean, callSessionId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required for the reservation popup.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        overlayView?.let {
            if (callSessionId != NO_CALL_SESSION && callSessionId == activeCallSessionId) {
                updateCaller(callerNumber)
            }
            it.visibility = View.VISIBLE
            return
        }

        val settings = store.getSettings()
        activeCallSessionId = callSessionId
        val content = OverlayContent(
            callerNumber = callerNumber.takeIf { settings.autoFillCallerNumber },
            callerName = contactNameForPhone(callerNumber),
            startMinimized = startMinimized,
            body = LinearLayout(this)
        )
        val root = buildForm(content)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(340),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(136)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        overlayView = root
        layoutParams = params
        windowManager.addView(root, params)
    }

    private fun buildForm(content: OverlayContent): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(White, dp(6), Line)
            elevation = dp(10).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(10), dp(10))
            background = roundedBackground(Black, dp(6), Color.TRANSPARENT)
        }
        val title = TextView(this).apply {
            text = "Reservation"
            setTextColor(White)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minimize = Button(this).apply {
            text = "-"
            minWidth = dp(44)
            minHeight = dp(36)
            styleHeaderButton()
            setOnClickListener {
                isMinimized = !isMinimized
                content.body.visibility = if (isMinimized) View.GONE else View.VISIBLE
                text = if (isMinimized) "+" else "-"
            }
        }
        val close = Button(this).apply {
            text = "X"
            minWidth = dp(44)
            minHeight = dp(36)
            styleHeaderButton()
            setOnClickListener { stopSelf() }
        }
        header.addView(title)
        header.addView(minimize)
        header.addView(close)
        makeDraggable(header) {
            if (isMinimized) {
                isMinimized = false
                content.body.visibility = View.VISIBLE
                minimize.text = "-"
            }
        }

        content.body.orientation = LinearLayout.VERTICAL
        content.body.setPadding(dp(14), dp(10), dp(14), dp(14))

        val customerName = input("Required").apply { setText(content.callerName.orEmpty()) }
        val phone = input("Optional").apply { setText(content.callerNumber.orEmpty()) }
        callerNameInput = customerName
        callerPhoneInput = phone
        customerName.trackCallerFieldEdits { callerNameEdited = true }
        phone.trackCallerFieldEdits { callerPhoneEdited = true }
        val reservationDateTime = dateTimeInput()
        val adults = input("Optional", InputType.TYPE_CLASS_NUMBER)
        val children = input("Optional", InputType.TYPE_CLASS_NUMBER)
        val duration = durationInput(store.getSettings().defaultDurationMinutes)
        val eventType = eventTypeInput()
        val preference = preferenceInput()
        val notes = input("Optional", singleLine = false)
        val errorText = TextView(this).apply {
            setTextColor(ErrorRed)
            textSize = 13f
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        content.body.addLabeledInput("Customer name *", customerName)
        content.body.addLabeledInput("Phone number", phone)
        content.body.addLabeledInput("Date and time *", reservationDateTime.input)
        content.body.addSplitLabeledInputs("Number of people", "Adults", adults, "Children", children)
        content.body.addLabeledInput("Duration", duration.input)
        content.body.addLabeledInput("Event type", eventType)
        content.body.addLabeledInput("Preference", preference)
        content.body.addLabeledInput("Notes", notes)
        content.body.addView(errorText)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        val draft = Button(this).apply {
            text = "Save draft"
            styleSecondaryButton()
            setOnClickListener {
                validateForm(customerName, reservationDateTime, adults, children, duration)?.let { message ->
                    errorText.showError(message)
                    return@setOnClickListener
                }
                store.saveReservation(
                    formReservation(
                        customerName,
                        phone,
                        reservationDateTime,
                        adults,
                        children,
                        duration,
                        eventType,
                        preference,
                        notes,
                        ReservationStatus.DRAFT,
                        null,
                        null
                    )
                )
                Toast.makeText(this@ReservationOverlayService, "Draft saved.", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }
        val create = Button(this).apply {
            text = "Create event"
            stylePrimaryButton()
            setOnClickListener {
                validateForm(customerName, reservationDateTime, adults, children, duration)?.let { message ->
                    errorText.showError(message)
                    return@setOnClickListener
                }
                val draftReservation = formReservation(
                    customerName,
                    phone,
                    reservationDateTime,
                    adults,
                    children,
                    duration,
                    eventType,
                    preference,
                    notes,
                    ReservationStatus.DRAFT,
                    null,
                    null
                )
                val result = CalendarRepository(this@ReservationOverlayService)
                    .createOrOpenFallback(draftReservation, store.getSettings().selectedCalendarId)
                val saved = when (result.status) {
                    CalendarCreateStatus.CREATED -> draftReservation.copy(
                        status = ReservationStatus.CREATED,
                        calendarEventId = result.eventId,
                        errorMessage = null
                    )
                    CalendarCreateStatus.OPENED_FALLBACK -> draftReservation.copy(
                        status = ReservationStatus.OPENED_IN_CALENDAR,
                        errorMessage = result.errorMessage
                    )
                    CalendarCreateStatus.FAILED -> draftReservation.copy(
                        status = ReservationStatus.FAILED,
                        errorMessage = result.errorMessage
                    )
                }
                store.saveReservation(saved)
                Toast.makeText(
                    this@ReservationOverlayService,
                    when (saved.status) {
                        ReservationStatus.CREATED -> "Calendar event created."
                        ReservationStatus.OPENED_IN_CALENDAR -> "Opened calendar to finish the event."
                        ReservationStatus.FAILED -> "Could not create the event. Saved to history."
                        ReservationStatus.DRAFT -> "Draft saved."
                    },
                    Toast.LENGTH_LONG
                ).show()
                if (saved.status != ReservationStatus.FAILED) stopSelf()
            }
        }
        buttons.addView(draft)
        buttons.addView(create)
        content.body.addView(buttons)

        if (content.startMinimized) {
            isMinimized = true
            content.body.visibility = View.GONE
            minimize.text = "+"
        }

        val scroll = ScrollView(this).apply { addView(content.body) }
        panel.addView(header)
        panel.addView(scroll)
        return panel
    }

    private fun updateCaller(callerNumber: String?) {
        val number = callerNumber?.trim()?.takeIf(String::isNotEmpty) ?: return
        val settings = store.getSettings()
        applyingCallerUpdate = true
        try {
            if (settings.autoFillCallerNumber && !callerPhoneEdited && callerPhoneInput?.text.isNullOrBlank()) {
                callerPhoneInput?.setText(number)
            }
            val contactName = contactNameForPhone(number)
            if (!contactName.isNullOrBlank() && !callerNameEdited && callerNameInput?.text.isNullOrBlank()) {
                callerNameInput?.setText(contactName)
            }
        } finally {
            applyingCallerUpdate = false
        }
    }

    private fun EditText.trackCallerFieldEdits(markEdited: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                if (!applyingCallerUpdate) markEdited()
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })
    }

    private fun input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT, singleLine: Boolean = true): EditText {
        return EditText(this).apply {
            this.hint = hint
            inputType = type
            textSize = FORM_INPUT_TEXT_SIZE_SP
            setTextColor(Black)
            setHintTextColor(InkMuted)
            background = roundedBackground(FieldFill, dp(5), Line)
            setSingleLine(singleLine)
            setPadding(dp(10), dp(7), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (singleLine) dp(FORM_INPUT_HEIGHT_DP) else LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dateTimeInput(): ReservationDateTimeInput {
        val value = ReservationDateTimeInput(
            input = input("Choose date and time", InputType.TYPE_NULL).apply {
                isFocusable = false
                isCursorVisible = false
                isClickable = true
            },
            date = today(),
            time = nextReservationTime()
        )
        value.updateDisplay()
        value.input.setOnClickListener { showDateTimePicker(value) }
        return value
    }

    private fun durationInput(defaultMinutes: Int): ReservationDurationInput {
        val value = ReservationDurationInput(
            input = input("Choose duration", InputType.TYPE_NULL).apply {
                isFocusable = false
                isCursorVisible = false
                isClickable = true
            },
            totalMinutes = defaultMinutes.coerceAtLeast(1)
        )
        value.updateDisplay()
        value.input.setOnClickListener { showDurationPicker(value) }
        return value
    }

    private fun eventTypeInput(): Spinner = selectionInput(EVENT_TYPES, mutedPosition = 0)

    private fun preferenceInput(): Spinner {
        return selectionInput(PREFERENCES).apply {
            setSelection(PREFERENCES.indexOf(DEFAULT_PREFERENCE))
        }
    }

    private fun selectionInput(items: List<String>, mutedPosition: Int? = null): Spinner {
        return Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@ReservationOverlayService,
                android.R.layout.simple_spinner_item,
                items
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return LinearLayout(this@ReservationOverlayService).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(7), dp(10), dp(6))
                        addView(TextView(this@ReservationOverlayService).apply {
                            text = getItem(position)
                            textSize = FORM_INPUT_TEXT_SIZE_SP
                            setTextColor(if (position == mutedPosition) InkMuted else Black)
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1f
                            )
                        })
                        addView(TextView(this@ReservationOverlayService).apply {
                            text = "▾"
                            textSize = 15f
                            setTextColor(DarkBeige)
                            gravity = Gravity.CENTER
                        })
                    }
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return TextView(this@ReservationOverlayService).apply {
                        text = getItem(position)
                        textSize = FORM_INPUT_TEXT_SIZE_SP
                        setTextColor(if (position == mutedPosition) InkMuted else Black)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), 0, dp(12), 0)
                        minHeight = dp(28)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(28)
                        )
                    }
                }
            }
            background = roundedBackground(FieldFill, dp(5), Line)
            setPopupBackgroundDrawable(roundedBackground(White, dp(5), Line))
            dropDownWidth = dp(312)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(FORM_INPUT_HEIGHT_DP)
            )
        }
    }

    private fun showDateTimePicker(value: ReservationDateTimeInput) {
        val selected = value.calendar()
        val dateDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selected.set(Calendar.YEAR, year)
                selected.set(Calendar.MONTH, month)
                selected.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker(value, selected)
            },
            selected.get(Calendar.YEAR),
            selected.get(Calendar.MONTH),
            selected.get(Calendar.DAY_OF_MONTH)
        )
        configureOverlayDialog(dateDialog)
        dateDialog.show()
    }

    private fun showTimePicker(value: ReservationDateTimeInput, selected: Calendar) {
        val timeDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                value.date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time)
                value.time = SimpleDateFormat("HH:mm", Locale.US).format(selected.time)
                value.updateDisplay()
            },
            selected.get(Calendar.HOUR_OF_DAY),
            selected.get(Calendar.MINUTE),
            false
        )
        configureOverlayDialog(timeDialog)
        timeDialog.show()
    }

    private fun showDurationPicker(duration: ReservationDurationInput) {
        val hoursPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 12
            value = (duration.totalMinutes / 60).coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
        }
        val minutesPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = Array(12) { index -> (index * 5).toString().padStart(2, '0') }
            value = (duration.totalMinutes % 60) / 5
            wrapSelectorWheel = false
        }
        val pickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(hoursPicker)
            addView(TextView(this@ReservationOverlayService).apply {
                text = "hours"
                setTextColor(Black)
                setPadding(dp(6), 0, dp(14), 0)
            })
            addView(minutesPicker)
            addView(TextView(this@ReservationOverlayService).apply {
                text = "min"
                setTextColor(Black)
                setPadding(dp(6), 0, 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Duration")
            .setView(pickerRow)
            .setPositiveButton("Done") { _, _ ->
                val selectedMinutes = hoursPicker.value * 60 + minutesPicker.value * 5
                duration.totalMinutes = selectedMinutes.coerceAtLeast(1)
                duration.updateDisplay()
            }
            .setNegativeButton("Cancel", null)
            .create()
        configureOverlayDialog(dialog)
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun configureOverlayDialog(dialog: AlertDialog) {
        dialog.window?.apply {
            setType(overlayWindowType())
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun LinearLayout.addLabeledInput(label: String, input: View) {
        val group = LinearLayout(this@ReservationOverlayService).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(6), 0, 0)
            }
        }
        group.addView(formLabel(label))
        group.addView(input)
        addView(group)
    }

    private fun LinearLayout.addSplitLabeledInputs(
        label: String,
        leftLabel: String,
        leftInput: View,
        rightLabel: String,
        rightInput: View
    ) {
        val group = LinearLayout(this@ReservationOverlayService).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(6), 0, 0)
            }
        }
        group.addView(formLabel(label))
        group.addView(
            LinearLayout(this@ReservationOverlayService).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(splitInputGroup(leftLabel, leftInput, endMargin = dp(4)))
                addView(splitInputGroup(rightLabel, rightInput, startMargin = dp(4)))
            }
        )
        addView(group)
    }

    private fun formLabel(label: String) = TextView(this).apply {
        text = label
        setTextColor(InkMuted)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(2), 0, 0, dp(2))
    }

    private fun splitInputGroup(
        label: String,
        input: View,
        startMargin: Int = 0,
        endMargin: Int = 0
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = startMargin
            marginEnd = endMargin
        }
        addView(formLabel(label))
        addView(input)
    }

    private fun validateForm(
        customerName: EditText,
        reservationDateTime: ReservationDateTimeInput,
        adults: EditText,
        children: EditText,
        duration: ReservationDurationInput
    ): String? {
        if (customerName.text.toString().trim().isBlank()) return "Customer name is required."
        if (!isValidDateTime(reservationDateTime.date, reservationDateTime.time)) return "Choose a valid date and time."
        val adultCount = adults.optionalPositiveInt()
        if (adults.text.toString().isNotBlank() && adultCount == null) {
            return "Adults must be a positive number."
        }
        val childCount = children.optionalPositiveInt()
        if (children.text.toString().isNotBlank() && childCount == null) {
            return "Children must be a positive number."
        }
        if ((adultCount?.toLong() ?: 0L) + (childCount?.toLong() ?: 0L) > Int.MAX_VALUE) {
            return "The total number of people is too large."
        }
        if (duration.totalMinutes <= 0) return "Choose a duration."
        return null
    }

    private fun EditText.optionalPositiveInt(): Int? {
        return text.toString().trim().toIntOrNull()?.takeIf { it > 0 }
    }

    private fun formReservation(
        customerName: EditText,
        phone: EditText,
        reservationDateTime: ReservationDateTimeInput,
        adults: EditText,
        children: EditText,
        duration: ReservationDurationInput,
        eventType: Spinner,
        preference: Spinner,
        notes: EditText,
        status: ReservationStatus,
        eventId: String?,
        error: String?
    ): Reservation {
        val adultCount = adults.optionalPositiveInt()
        val childCount = children.optionalPositiveInt()
        val partySize = if (adultCount == null && childCount == null) {
            null
        } else {
            ((adultCount?.toLong() ?: 0L) + (childCount?.toLong() ?: 0L)).toInt()
        }
        return Reservation(
            customerName = customerName.text.toString().trim(),
            phoneNumber = phone.text.toString().trim().ifBlank { null },
            reservationDate = reservationDateTime.date,
            reservationTime = reservationDateTime.time,
            durationMinutes = duration.totalMinutes,
            partySize = partySize,
            tablePreference = preference.selectedItem.toString().takeUnless { it == DEFAULT_PREFERENCE },
            eventType = eventType.selectedItem.toString().takeIf { eventType.selectedItemPosition > 0 },
            notes = notes.text.toString().trim().ifBlank { null },
            internalNotes = null,
            calendarEventId = eventId,
            status = status,
            errorMessage = error,
            adultCount = adultCount,
            childCount = childCount
        )
    }

    private fun makeDraggable(view: View, onTap: () -> Unit) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    if (!dragged && kotlin.math.hypot(deltaX.toDouble(), deltaY.toDouble()) > dp(6)) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = startX + deltaX.toInt()
                        params.y = (startY + deltaY.toInt()).coerceAtLeast(0)
                        overlayView?.let { windowManager.updateViewLayout(it, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reservation popup",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val closeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReservationOverlayService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reservation helper is open")
            .setContentText("Tap to review reservations or close from the popup.")
            .setContentIntent(openIntent)
            .addAction(R.mipmap.ic_launcher, "Close", closeIntent)
            .setOngoing(true)
            .build()
    }

    private fun TextView.showError(message: String) {
        text = message
        visibility = View.VISIBLE
    }

    private fun roundedBackground(fill: Int, radius: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = radius.toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun Button.styleHeaderButton() {
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(White)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(42, 42, 42))
        stateListAnimator = null
    }

    private fun Button.stylePrimaryButton() {
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(White)
        backgroundTintList = ColorStateList.valueOf(Black)
        minHeight = dp(40)
    }

    private fun Button.styleSecondaryButton() {
        textSize = 14f
        setTextColor(DarkBeige)
        backgroundTintList = ColorStateList.valueOf(FieldFill)
        minHeight = dp(40)
    }

    private fun isValidDateTime(date: String, time: String): Boolean {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        parser.isLenient = false
        return runCatching { parser.parse("$date $time") }.getOrNull() != null
    }

    private fun contactNameForPhone(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank() ||
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return runCatching {
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())

    private fun nextReservationTime(): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30)
            if (get(Calendar.MINUTE) <= 30) {
                set(Calendar.MINUTE, 30)
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            set(Calendar.SECOND, 0)
        }
        return SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class OverlayContent(
        val callerNumber: String?,
        val callerName: String?,
        val startMinimized: Boolean,
        val body: LinearLayout
    )

    private data class ReservationDateTimeInput(
        val input: EditText,
        var date: String,
        var time: String
    ) {
        fun calendar(): Calendar {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                isLenient = false
            }
            val parsed = runCatching { parser.parse("$date $time") }.getOrNull()
            return Calendar.getInstance().apply {
                if (parsed != null) this.time = parsed
            }
        }

        fun updateDisplay() {
            val display = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US).format(calendar().time)
            input.setText(display)
        }
    }

    private data class ReservationDurationInput(
        val input: EditText,
        var totalMinutes: Int
    ) {
        fun updateDisplay() {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val display = when {
                hours > 0 && minutes > 0 -> "$hours hr $minutes min"
                hours > 0 -> "$hours hr"
                else -> "$minutes min"
            }
            input.setText(display)
        }
    }

    companion object {
        private const val FORM_INPUT_TEXT_SIZE_SP = 13.5f
        private const val FORM_INPUT_HEIGHT_DP = 38

        private val EVENT_TYPES = listOf(
            "-- Select an Event --",
            "Corporate Event",
            "Wedding",
            "Confirmation",
            "Engagement",
            "Communion",
            "Bridal Shower",
            "Sweet 16",
            "Baptism",
            "Birthday",
            "Other"
        )
        private val PREFERENCES = listOf("Patio", "Indoor", "None")
        private const val DEFAULT_PREFERENCE = "None"

        private const val ACTION_SHOW = "com.example.restaurant_call_assistant.SHOW_RESERVATION_OVERLAY"
        private const val ACTION_CLOSE = "com.example.restaurant_call_assistant.CLOSE_RESERVATION_OVERLAY"
        private const val EXTRA_PHONE = "extra_phone"
        private const val EXTRA_START_MINIMIZED = "extra_start_minimized"
        private const val EXTRA_CALL_SESSION_ID = "extra_call_session_id"
        private const val NO_CALL_SESSION = 0L
        private const val CHANNEL_ID = "reservation_overlay"
        private const val NOTIFICATION_ID = 42
        private const val Black = Color.BLACK
        private const val White = Color.WHITE
        private val Line = Color.rgb(230, 226, 222)
        private val DarkBeige = Color.rgb(104, 78, 66)
        private val InkMuted = Color.rgb(104, 97, 93)
        private val FieldFill = Color.rgb(248, 246, 244)
        private val ErrorRed = Color.rgb(161, 32, 22)

        fun showIntent(
            context: Context,
            callerNumber: String? = null,
            startMinimized: Boolean = false,
            callSessionId: Long = NO_CALL_SESSION
        ): Intent {
            return Intent(context, ReservationOverlayService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_PHONE, callerNumber)
                .putExtra(EXTRA_START_MINIMIZED, startMinimized)
                .putExtra(EXTRA_CALL_SESSION_ID, callSessionId)
        }
    }
}
